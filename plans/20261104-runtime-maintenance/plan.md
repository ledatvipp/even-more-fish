# Runtime maintenance fixes

Status: source/build/paired native overlays verified; controller review/runtime activation pending. Preserve all dirty work; FishCatalogAdapter belongs to clone_rank.

Proven contracts: repeated optional registration accumulates core HeadDB listeners; both core and J21 addon reload on one HDB event. Make core HDB registration idempotent, initialize addon API at LOWEST without reload, and let core MONITOR own one reload after readiness. Preserve ordinary manual reload, head items and addon registry lifetimes. No EvenMoreFish lifecycle rewrite or other optional hook change.

Preserve returned plugin versions without Minecraft suffix; validate malformed update responses without crashing. Add bounded exception-class/cause diagnostics for updater failures; never log arbitrary exception messages/config values or replace user lore to quiet warning.

Verify focused tests, full build and required headless regression. Qualify source-compiled changed entries against native body baseline, then produce paired native-compatible core/external-J21 overlays with identical embedded addon payload and all other native bytes retained. Carry clone's reviewed catalog fix. No remote changes, flags, new gameplay features, data removal or commits. Runtime acceptance remains separate.
