# Kiểm tra sau restart Skyblock / BoxPvP — 2026-11-14

## Kết luận

Cả hai server đang có đúng JAR đã upload: **3.200.506 bytes**, SHA-256 `5c24e5e94c8feaf2fead49f478b37ae7cbd67e6feacaffd6d83a94d088bab347`. Mỗi thư mục plugins có một JAR mang tên EMF; không có bản EMF thứ hai trong danh sách tên file EMF. Mtime JAR đều trước thời điểm khởi động mới. Log có Loading / Enabling / Enabled và server Done sau restart, không thấy EMF bị disable hoặc lỗi khởi tạo Auto Fishing trong các snapshot đã kiểm tra.

Version vẫn là `2.3.6-SNAPSHOT`; kết luận đúng artifact dựa trên hash JAR, không chỉ chuỗi version. Đây là xác minh file và startup; chưa phải nghiệm thu trong game.

## Skyblock

- `skyblock-latest-final.log:302`: Loading EvenMoreFish v2.3.6-SNAPSHOT.
- `:823` / `:852`: Enabling rồi `EvenMoreFish by Oheers : Enabled`.
- `:837`: 5 rarity / 72 fish; log cũng ghi 10 baits, 7 rods, 3 competitions / 30 schedules.
- `:836` / `:2991`: Simulator loaded với `enabled=false`.
- `:1325` trong snapshot đầu: LeDatIslandSSB đăng ký Fishing Lake T10 integration.
- `:3082`: server `Done (95.658s)`.
- Snapshot cuối 3.150 dòng: **0 ERROR của EMF**, 0 stack trace / runtime exception liên quan EMF.
- Có 10 cảnh báo cấu hình mồi khác nhau, lặp lại khi reload, cùng một thông báo update upstream: tổng 21 WARN của EMF. Bốn tham chiếu rarity không tồn tại: `Mythic` ở `moiHaiThan`, `moiLongHon`, `moiNguyenRua`; `Anomaly` ở `moiNguyenRua`. Sáu tham chiếu fish không tồn tại ở mồi `shrimp`: Common/`CaChepSong`, `CaTrichBac`, `CaNucBien`; Rare/`CaNganVay`, `CaHoPhach`, `CaLamTuyen`.

## BoxPvP

- `boxpvp-latest-final.log:245`: Loading EvenMoreFish v2.3.6-SNAPSHOT.
- `:635` / `:656`: Enabling rồi `EvenMoreFish by Oheers : Enabled`.
- `:649`: 5 rarity / 72 fish; log ghi 6 baits, 7 rods, 3 competitions / 30 schedules. Các cấu hình mặc định được tạo khi cài EMF lần đầu.
- `:1510`: server `Done (54.266s)`.
- Snapshot cuối 1.602 dòng: **3 ERROR của EMF cùng một nguyên nhân**, xuất hiện lúc enable và hai lần reload; không thấy stack trace hoặc lỗi khởi tạo Auto Fishing.

Lỗi nguyên văn:

```text
[Simulator] Invalid simulator/cinematics.yml; keeping old snapshot: presets.legendary.cues.hero_reveal.particle must be a simple particle
```

Đối chiếu read-only file runtime: BoxPvP đang có `TOTEM_OF_UNDYING` tại khóa trên; Skyblock đang có `ENCHANT`. Resource mặc định trong source cũng khai báo `TOTEM_OF_UNDYING`, nhưng allowlist của `PremiumCinematicConfigLoader` không chứa giá trị đó. Bộ loader trả candidate null; `SimulatorModule.reload()` từ chối thay snapshot nếu bất kỳ candidate nào null. Lỗi này thuộc cấu hình cinematic của Simulator. `EvenMoreFish.onEnable()` vẫn tiếp tục tạo `VanillaCatchCinematicService` và `AutoFishingService`, sau đó log Enabled. Không sửa hoặc bật Simulator trong lượt kiểm tra này.

Cảnh báo còn lại ở BoxPvP là thông báo update upstream; thông báo này không chứng minh đang nạp JAR cũ.

## Phạm vi bằng chứng

Chỉ đọc SFTP, tải JAR/log để đối chiếu và ghi báo cáo cục bộ; không thay JAR hoặc cấu hình server, không chạy reload/restart/lệnh game. Log gốc nằm ngoài repo để không đưa dữ liệu người chơi vào báo cáo. Bằng chứng lọc không chứa thông tin đăng nhập: [post-restart-evidence.json](post-restart-evidence.json).

Chưa thử GUI, quota/permission, cast/reel, head/cinematic và burst với client thật hoặc dưới tải. Không suy ra lỗi runtime không bao giờ xuất hiện chỉ từ cửa sổ log sau startup. Lỗi cấu hình Simulator và các tham chiếu mồi Skyblock hiện vẫn chưa được chỉnh sửa.
