# Sách phù phép khi câu cá

Sau một lượt câu thành công, người chơi có cơ hội nhận sách do AdvancedEnchantments tạo. Sách dùng cơ chế kéo thả và lore hiện có của AE. Túi đầy thì sách chờ, không rơi xuống đất; thử giao lại khi đóng túi hoặc đăng nhập. Trường hợp mất kết nối ngay lúc giao chưa thể xác minh sẽ chờ kiểm tra, không tự cấp lại.

## Phạm vi và tỷ lệ

- EMF: lượt câu native đã giao/thay item thành công, không bị hủy, Survival. Manual 0,5%; EMF Auto Fishing 0,25%. Dùng chung cooldown 60 giây và tối đa 6 sách trong một giờ trượt, lưu bền qua restart.
- Một lượt câu chỉ được xét một lần theo UUID hook. Double catch/fortune tăng số cá không tăng số lượt xét sách. Preview, bait loot, native fallback không đi qua boundary giao cá EMF thì không phát sách.
- Cấp sách chọn đều I..min(II, cấp tối đa); tỷ lệ ép 100%, phá hủy 0%. Chỉ chọn đăng ký đang enabled và có sẵn trong enchanter của provider.
- Trọng số SIMPLE / UNIQUE / ELITE / ULTIMATE / LEGENDARY / FABLED: 50 / 27 / 15 / 6 / 1,5 / 0,5. Chuẩn hóa trên nhóm hợp lệ; chọn đều ID trong nhóm. DUNGEON/SOUL không tham gia.
- Không đổi catalog, shop, tiền tệ, cá, cần, mồi, combo, storage, quota Auto Fishing hoặc Simulator. Không claim nghiệm thu runtime/Folia từ kiểm tra tĩnh.

## Cấu hình và nhận dạng

`plugins/EvenMoreFish/ae-fishing-rewards.yml` mặc định OFF. Cần AE và LeDatPlatform; thiếu provider thì câu cá native tiếp tục. Mặc định dùng các đăng ký hợp lệ đang cài. `catalog` tùy chọn chỉ dành cho composition SkyRNG đúng 53 ID đã tồn tại; không tự đăng ký enchant.

Hook gọi bên trong `FishingProcessor.afterFishEvent`, sau khi có pending catch đã giao và kiểm tra hủy tại MONITOR. Không đăng ký thêm PlayerFishEvent listener và không dùng EMFFishCaughtEvent trước commit. Typed catch rewards hiện có không mang UUID receipt và chạy trước delivery, nên không dùng chúng để giả lập random bonus.

`ae-fishing-rewards.db` chỉ chứa receipt sách. Quyết định MISS/LIMITED/PLANNED và quota được chốt cùng transaction trước khi tạo sách native. READY giữ bytes sách đầu tiên. Claim có compare-and-set; CLAIMING chưa rõ kết quả không tự trả lại READY. Native fish không bị rollback hay thay bytes do sách bonus.

## SkyRNG: provider owns the commit

Hai SkyRNG không cài EMF. Không cài toàn bộ EMF để thêm thưởng. LeDatPickaxe sở hữu settlement câu cá và gọi `LeDatRngPlugin.onFishingCatchCommitted(UUID operationId, UUID playerId, boolean automatic)` sau credit dương thật. Manual dùng UUID hook gốc; auto gán UUID một lần tại origin, trước settlement trễ. Owner và publication claim được xác nhận trước callback. Không tạo UUID mới trên retry hoặc dùng HUD/timer/raw MONITOR thay bằng chứng commit.

RNG agent sở hữu một JAR composition cuối cùng, gồm mining/rare/ore/Codex/fishing, dùng native gateway và delivery protocol hiện có; không sao chép backend SQLite hay cài plugin EMF. RNG giữ pool53, sách cấp I, success80/destroy0 và trọng số destination45/30/15/7/2,5/0,5; xác suất/cooldown/quota như trên. Skyblock vẫn dùng pool đang cài, cấp I–II và success100/destroy0. Kiểm tra linkage của đúng cặp Pickaxe/RNG JAR, backup và cold SHA trước khi operator restart.

## Kiểm tra và triển khai

Target build: `gradlew.bat :even-more-fish-plugin-1-21:build -x test --max-workers=1 -PledatPlatformApiJar=<actual-api.jar>`. Chạy focused FishingBookLedgerTest/FishingBookRollTest và regression SingleSlotDeliveryPlannerTest, RareCatchPolicyTest, AutoFishWeeklyQuotaTest với API 1.20.1 mà core khai báo. Không cần tạo world/server fixture lớn.

Candidate deployment dùng đúng baseline đang cài, chỉ thêm helper + lifecycle/post-delivery hooks; inverse bytecode proof và member parity bảo toàn variant cũ. Backup, stage pending, cold SHA readback; người vận hành tự restart. Sau restart cần thử manual/auto, cancel/miss, full inventory/reconnect, giữ ID/stats cá/cần và xác nhận AE book/effect thật. Không dùng reload thay restart.
