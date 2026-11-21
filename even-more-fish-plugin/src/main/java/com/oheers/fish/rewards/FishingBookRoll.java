package com.oheers.fish.rewards;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FishingBookRoll {
    private static final SecureRandom PRIVATE_DRAW = new SecureRandom();
    public record Enchant(String id, String group, int maximum) { }
    private FishingBookRoll() { }
    static Random random() { return PRIVATE_DRAW; }
    public static boolean wins(double chance, Random random) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Invalid catch book chance");
        return random.nextDouble() < chance;
    }
    public static Enchant choose(List<Enchant> pool, Map<String, Double> weights, Random random) {
        var groups = pool.stream().map(Enchant::group).distinct().sorted().filter(g -> weights.getOrDefault(g, 0.0) > 0).toList();
        double total = groups.stream().mapToDouble(g -> weights.get(g)).sum();
        if (!(total > 0) || !Double.isFinite(total)) throw new IllegalArgumentException("No eligible AE groups");
        double roll = random.nextDouble() * total; String group = groups.getLast();
        for (String candidate : groups) { roll -= weights.get(candidate); if (roll < 0) { group = candidate; break; } }
        String selected = group;
        var candidates = pool.stream().filter(e -> e.group().equals(selected)).sorted(java.util.Comparator.comparing(Enchant::id)).toList();
        return candidates.get(random.nextInt(candidates.size()));
    }
}
