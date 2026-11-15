# Auto Fishing — xác minh và upload (2026-11-14)

## Kết quả

Đã upload cùng một JAR EvenMoreFish lên Skyblock và BoxPvP, đọc lại toàn bộ file qua SFTP và xác nhận SHA-256 khớp. Không ghi cấu hình runtime hoặc JAR của plugin khác; danh sách, kích thước và mtime các JAR khác giữ nguyên trước/sau từng upload. Chưa restart server hoặc xác nhận client thật.

## Thay đổi

- Lấy phao bằng `Player#getFishHook()` thay vì quét toàn world mỗi tick; giảm jitter quăng và bỏ micro-break tự chèn. Giữ luồng native, upgrade, catch event và phần thưởng hiện có.
- Quota mặc định 1 giờ/tuần; `emf.autofish.weekly.180`, `.420`, `.840` cấp 3/7/14 giờ. Lấy quyền cao nhất, không cộng dồn. Reset thứ Hai 00:00 `Asia/Ho_Chi_Minh`.
- Lưu `weekly-start` và `weekly-used-seconds` theo UUID; đổi permission không xóa lượng đã dùng. Dùng quota tuần trước rồi số dư mua/dùng thử; reset không xóa số dư cũ.
- Nút Auto có lore gọn, thanh 20 đoạn và phần trăm quota tuần đã dùng; cập nhật mỗi giây khi Auto hoạt động, cập nhật trạng thái khi tự dừng. Chi tiết thời gian ở sách thông tin.
- Head cinematic dùng billboard VERTICAL, giảm rung rương; nội suy/cập nhật mỗi 2 tick, vẫn giữ đủ heartbeat ở tick lẻ.
- Catch native được chấp nhận có hạng Legendary+ hoặc dài từ 200 cm hoặc giá trị từ 50.000 phát burst particle/âm thanh tại phao. Bán kính 32 block, tối đa 32 người xem, cooldown 10 giây/người câu; tôn trọng visibility và effects/HUD/particle/sound cá nhân. Không thêm phần thưởng, entity nổ hoặc sát thương.
- Không thay đổi các cờ Simulator/reward bridge.

## Kiểm thử

- Focused JUnit AutoFish profile/quota/store/bridge/service, cinematic và timeline: PASS, xem [focused-tests.log](focused-tests.log).
- Core JUnit cuối: **235 tests, 60 suites, 0 failures, 0 errors, 0 skipped**.
- Full Gradle `:even-more-fish-plugin-1-21:shadowJar build`: PASS, gồm adapter 1.20, 1.21 và 26.1. Xem [final-build.log](final-build.log).
- `qa/run-phase9-regression.sh`: PASS; gồm 57 YAML resources và 18 locale diagnostics. Đây là headless/synthetic evidence, không phải Paper MSPT/client acceptance. Xem [phase9-autofish.log](phase9-autofish.log).
- Review phát hiện heartbeat bị bỏ qua khi chuyển sang cadence 2 tick; đã sửa bằng cách kiểm tra cả hai tick trong frame và thêm test đếm nhịp từng tier. Không còn finding có thể hành động.
- Hai lần build offline trước thiếu dependency trong cache (JDBI, rồi CommandAPI của 1.20). Đã khắc phục bằng build online; không bỏ hoặc làm yếu test. Log thất bại được giữ lại.
- UTF-8 và `git diff --check`: PASS. Chưa commit/push.

## Artifact và SFTP

Artifact: `even-more-fish-2.3.6-SNAPSHOT-1.21-20261114-1050.jar`; 3,200,506 bytes; plugin version `2.3.6-SNAPSHOT`. JAR đã kiểm tra ZIP CRC, class quota/heartbeat mới và ba permission metadata đều có default false.

SHA-256: `5c24e5e94c8feaf2fead49f478b37ae7cbd67e6feacaffd6d83a94d088bab347`.

| Server | File đã upload | Kết quả |
|---|---|---|
| Skyblock | `/plugins/even-more-fish-2.3.6-SNAPSHOT-1.21.jar` | Thay đúng EMF, hash khớp |
| BoxPvP | `/plugins/even-more-fish-2.3.6-SNAPSHOT-1.21.jar` | Thêm EMF mới, hash khớp |

BoxPvP chưa có JAR EMF ở lần kiểm tra đầu; các dependency tùy chọn Vault/PlaceholderAPI/PacketEvents đã có. Plugin sẽ khởi tạo cấu hình/dữ liệu EMF mặc định khi được nạp lần đầu. Không chỉnh các cấu hình đó qua SFTP.

Backup Skyblock: `/.codex-updates/20261114-emf-autofish-weekly/even-more-fish-before.jar`, SHA-256 `3f35d428e34d6549aaaccc0900f263afd2ae97b1bec4e31df1b60f7ad2e5d7ef`; đã đọc lại và xác nhận. SFTP không hỗ trợ posix-rename nên giữ thêm bản trước đổi tên ở `/.codex-updates/20261114-emf-autofish-weekly/emf-before-rename.jar` trước khi đưa JAR staging vào đúng tên active. Không có thời điểm ghi đè JAR đang hoạt động bằng dữ liệu tải dở.

Bằng chứng máy đọc: [artifact.json](artifact.json), [deployment.json](deployment.json), [sftp-before.json](sftp-before.json). Không chứa thông tin đăng nhập.

## Giới hạn và bước sau restart

Cần clean restart cả hai server; không dùng `/reload` để nạp JAR. Chưa kiểm tra startup, độ mượt/head trên client, visual với người đứng cạnh hoặc hiệu suất dưới tải thực tế. Quota test dùng lịch xác định, chưa chờ một tuần thật trên production. Các gate Simulator staging không được chứng nhận bởi bản native này.

Sau restart: kiểm tra `/autofish`, thanh quota tăng, các mức permission, bật/tắt và dừng khi đầy túi/hết thời gian; xác nhận cá Legendary/cá lớn/giá trị cao phát hiệu ứng cho người cạnh đó và opt-out/vanish được tôn trọng. Giữ bản sao `autofish-data.yml` trước mọi thao tác thủ công với dữ liệu.

Rollback: thay lại JAR Skyblock từ backup; với BoxPvP, gỡ đúng JAR EMF mới nếu cần hoàn tác, rồi restart. Không xóa dữ liệu người chơi. Working tree ban đầu đã có các chỉnh sửa EMF không thuộc yêu cầu lần này; chúng được giữ nguyên và có trong bản build toàn repo, không bị revert hoặc tách ghép class thủ công.

## Cập nhật sau khi người dùng restart

[Kiểm tra hậu restart ngày 2026-11-14](post-restart-verification.md) xác nhận hash và Enabled trên cả hai server; ghi riêng cảnh báo mồi Skyblock và lỗi preset Simulator BoxPvP. Các giới hạn trước restart ở trên là trạng thái tại thời điểm upload.
