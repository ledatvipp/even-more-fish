import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Adds only the optional observer to a captured deployed EMF variant; proves inverse parity. */
final class FishingAEBaselinePatch {
    private static final String HOOK = "com/oheers/fish/rewards/FishingAERewards";
    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Path.of(args[0])); var node = read(original);
        int start = 0, stop = 0, accepted = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray())
                if (instruction instanceof MethodInsnNode call && call.owner.equals(HOOK)) throw new IllegalStateException("Already patched");
            if (node.name.equals("com/oheers/fish/EvenMoreFish") && method.name.equals("onEnable")) {
                for (var instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) {
                    var hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "start", "(Lorg/bukkit/plugin/java/JavaPlugin;)V", false));
                    method.instructions.insertBefore(instruction, hook); start++;
                }
            }
            if (node.name.equals("com/oheers/fish/EvenMoreFish") && method.name.equals("onDisable")) {
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "stop", "()V", false)); stop++;
            }
            if (node.name.equals("com/oheers/fish/fishing/FishingProcessor") && method.name.equals("afterFishEvent")) {
                for (var instruction : method.instructions.toArray())
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("com/oheers/fish/fishing/FishingProcessor$PendingCatchVisual") && call.name.equals("doubleCatch") && accepted == 0) {
                        var previous = instruction.getPrevious();
                        if (!(previous instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 3)
                            throw new IllegalStateException("Unexpected delivered catch local; inspect actual baseline");
                        var hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "accepted", "(Lorg/bukkit/event/player/PlayerFishEvent;)V", false));
                        method.instructions.insertBefore(previous, hook); accepted++;
                    }
            }
        }
        if (node.name.equals("com/oheers/fish/EvenMoreFish") ? start != 1 || stop != 1 : accepted != 1)
            throw new IllegalStateException("Unexpected baseline hook counts");
        byte[] patched = bytes(node); var reversed = read(patched);
        for (var method : reversed.methods) for (var instruction : method.instructions.toArray())
            if (instruction instanceof MethodInsnNode call && call.owner.equals(HOOK)) {
                if (!call.name.equals("stop")) method.instructions.remove(instruction.getPrevious());
                method.instructions.remove(instruction);
            }
        if (!java.util.Arrays.equals(bytes(read(original)), bytes(reversed))) throw new IllegalStateException("Unrelated bytecode changed");
        var path = Path.of(args[1]); Files.createDirectories(path.toAbsolutePath().getParent()); Files.write(path, patched);
    }
    private static ClassNode read(byte[] data) { var node = new ClassNode(); new ClassReader(data).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
}
