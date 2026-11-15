# Runtime integration maintenance

The HeadDatabase item addon initializes its API at Bukkit event priority LOWEST. The core HeadDBIntegration listener runs at MONITOR and owns the single reload needed to rebuild items after the database becomes available. DependencyManager keeps one core HDB listener across manual and integration reloads; optional registration does not add another copy. Bukkit removes the plugin's registered listeners during normal disable. Do not add another reload to the item addon or register a fresh core HDB listener on every reload.

This ownership change is a paired core/J21 addon release. The external J21 addon and the copy embedded in the core must contain the same patched class bytes. Activate both while the operator has stopped the server, preserving backups and existing fish/player/configuration files. Updating the core's embedded addon alone does not replace an existing external addon. No Simulator, visual, AI or reward flag is enabled by this maintenance.

Update checks accept ordinary release/SNAPSHOT version strings and remove only a trailing numeric Minecraft version suffix, such as `-1.21` or `-1.21.4`. Empty remote version arrays keep the installed version; malformed response shapes follow the existing logged fallback instead of producing substring errors.

ConfigBase updater warnings now include up to four exception/cause class names. Exception messages, YAML content and paths are omitted from this new diagnostic. A warning still means the update failed; adding diagnostics does not resolve its underlying I/O/resource/versioning cause. Keep the user's messages/lore intact and investigate the reported exception types before changing configuration content.

Source/unit/overlay verification does not replace native server acceptance. After an approved paired release and restart, verify one HDB event produces one reload, head items resolve, normal reload remains functional, plain-version update checks produce no substring exception, and any messages update warning includes its bounded cause types. Simulator Paper/client gates remain separate and unverified unless recorded by their own runbook.
