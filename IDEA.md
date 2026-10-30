Pain point: Có nhiều người thường chụp nhiều ảnh chụp màn hình trên máy để lưu thông tin hoặc ghi nhớ các việc để làm sau như một địa chỉ, lịch họp, kế hoạch hay hình ảnh thông tin nào đó để xem lại. Nhưng các ảnh này thường chỉ nằm trong thư viện ảnh mà không được xem lại và bị quên đi.
Để xử lý pain point này, thiết kế một ứng dụng chạy nền trên máy và theo dõi khi người dùng chụp ảnh màn hình rồi sau đó lấy những thông tin trong ảnh bằng các dùng OCR của thiết bị và AI để tự tạo lịch cho những ảnh phù hợp và phân loại các ảnh theo các danh mục để giúc người dùng dễ dàng xem lại các ảnh theo nhóm. Giúp các ảnh chụp màn hình thành các thông tin và lịch trình có ích thay vì bị lãng quên. Và thêm chức năng kiểm tra và giúp người dùng xóa hoặc thông báo cho người dùng các ảnh màn hình đã cũ hoặc đã xử lý xong không cần thiết nữa giúp loại bỏ clutter.

Thiết kế ứng dụng:

Phần 1: Kiến trúc hệ thống tổng thể (Hybrid + On-Device PII Security)
+---------------------------------------------------------------------------------------------+
|                                ON-DEVICE (Client App)                                       |
|                                                                                             |
|  [Chụp ảnh màn hình] ──> [File Watcher nhẹ] ──> [Hàng đợi SQLite]                           |
|                                                     │                                       |
|  [Lưu trữ ảnh mã hóa cục bộ] <── [Apple Vision / Google ML Kit OCR]                         |
|            │                                        │                                       |
|            │ (Ảnh gốc GIỮ NGUYÊN trên máy)          ▼                                       |
|            │                          [Bộ lọc PII cục bộ (Regex & Heuristics)]              |
|            │                          (Che/xóa thông tin nhạy cảm: số thẻ, mật khẩu...)     |
|            │                                        │                                       |
|            │                                        ▼ (Chỉ gửi văn bản đã làm sạch PII)     |
+------------│----------------------------------------│---------------------------------------+
             │                                        │
             │                                        ▼
             │                          +--------------------------+
             │                          |     CLOUD AI BACKEND     |
             │                          |  (Phân tích ý định JSON) |
             │                          +--------------------------+
             │                                        │
             │ (Metadata cấu trúc & JSON phản hồi) ───┘
             ▼
  [Database cục bộ SQLite: Tìm kiếm FTS5/Vector + Phân loại danh mục + Đồng bộ Lịch/Nhắc nhở]

Phần 2: Pipeline xử lý từ đầu đến cuối (End-to-End Pipeline)
Bước 1: Thu thập thụ động & Hàng đợi không tốn tài nguyên (Zero-Friction Passive Capture)
Người dùng chụp ảnh màn hình bằng phím cứng như bình thường.

Trình quan sát hệ thống (PHPhotoLibraryChangeObserver / ContentObserver) ghi nhận đường dẫn tệp vào hàng đợi SQLite trong vài mili-giây và đóng tiến trình. Không gây sụt pin, không bị hệ điều hành tiêu diệt (OS Termination).

Bước 2: OCR cục bộ & Lọc sơ bộ ảnh rác (Pre-Filtering)
Ứng dụng chạy OCR cục bộ bằng Apple Vision hoặc Google ML Kit.

Bộ lọc ảnh rác: Hệ thống kiểm tra nhanh xem ảnh có chứa từ khóa hành động hay văn bản có ích hay không. Nếu là meme, ảnh chụp màn hình khóa trắng, nó sẽ bị loại bỏ ngay lập tức và không tốn quota hay gọi API.

Bước 3: Bộ lọc PII cục bộ (On-Device PII Scrubbing - MỚI)
Trước khi bất kỳ đoạn văn bản OCR nào rời khỏi thiết bị, một mô-đun lọc PII cục bộ (sử dụng các biểu thức chính quy - Regex và quy tắc nhận diện mẫu) sẽ tự động quét và che/xóa các thông tin nhạy cảm như:

Số thẻ tín dụng / tài khoản ngân hàng.

Mật khẩu, mã OTP, số điện thoại cá nhân.

Căn cước công dân / Hộ chiếu.

Kết quả: Chuỗi văn bản sau khi được làm sạch hoàn toàn an toàn mới được phép truyền tải qua mạng.

Bước 4: Kiểm tra định mức & Trạng thái tài khoản (Tier & Quota Check)
Nếu là người dùng Pro hoặc Free còn hạn mức: Gửi chuỗi văn bản đã làm sạch PII lên Cloud AI để phân tích ý định, bóc tách thời gian, tiêu đề và danh mục với độ chính xác cao.

Nếu là người dùng Free đã HẾT HẠN MỨC trong tháng: Áp dụng cơ chế Graceful Degradation (Hạ cấp linh hoạt):

Không chặn người dùng hay hiển thị paywall gây ức chế.

Chuyển sang chế độ Gắn nhãn thủ công (Manual Tagging) hoặc xử lý tiêu chuẩn tại chỗ mà không dùng Cloud AI, kèm theo một biểu ngữ (banner) mềm mại, thân thiện mời gọi nâng cấp: "Bạn đã dùng hết 50 lượt AI thông minh tháng này. Nâng cấp Pro để tiếp tục tự động hóa không giới hạn!"

Bước 5: Tự động hóa Nhắc nhở & Sắp xếp Thư viện
Đẩy nhắc nhở vào hệ thống Lịch/Nhắc nhở gốc của điện thoại (EventKit / CalendarContract).

Phân loại mục vào các danh mục cục bộ (To-Buy, Event, Reading List, Task).

Bước 6: Tìm kiếm thông minh & Dọn dẹp định kỳ (Aging Housekeeping)
Tìm kiếm: Lưu trữ metadata vào SQLite tích hợp FTS5 (và Vector Search đối với gói Pro) để tìm kiếm ngoại tuyến tức thì.

Aging Monitor: Kiểm tra định kỳ thời gian lưu trữ. Nếu ảnh quá hạn theo cài đặt, hiển thị thông báo dọn dẹp nhẹ nhàng.

Phần 3: Tóm tắt Mô hình Kinh doanh & Đóng gói Tính năng (Freemium Packaging)
Chiến lược phân chia sản phẩm giúp tối ưu hóa chuyển đổi từ miễn phí lên trả phí mà không gây ác cảm cho người dùng:
Tính năng / Trải nghiệm  |  Gói Free (Miễn phí có giới hạn)           |  Gói Pro (Trả phí không giới hạn)
Hạn mức xử lý AI         |  Tối đa 50 ảnh/tháng (Có bộ lọc ảnh rác)   |  Không giới hạn
Bảo mật ảnh & PII        |  Bảo mật tuyệt đối (Ảnh lưu máy, lọc PII)  |  Bảo mật tuyệt đối (Ảnh lưu máy, lọc PII)
Cốt lõi Nhắc nhở         |  Tạo nhắc nhở cơ bản & lưu trữ cục bộ      |  Tạo nhắc nhở cơ bản & lưu trữ cục bộ
Phân loại danh mục       |  Danh mục chuẩn cố định (Mặc định)         |  Custom AI Categories (Tự tạo danh mục riêng)
Tìm kiếm thư viện        |  Tìm kiếm văn bản đầy đủ cục bộ (FTS5)     |  Advanced Vector Semantic Search (Tìm kiếm ngữ nghĩa nâng cao)
Đồng bộ bên thứ ba       |  Không hỗ trợ                              |  Tự động đồng bộ Notion / Obsidian
Quy tắc dọn dẹp (Aging)  |  Cài đặt thời gian nhắc nhở chung          |  Custom Aging Housekeeping Rules (Tùy chỉnh linh hoạt)
Khi hết hạn mức Free     |  Graceful Degradation: Chuyển sang gắn nhãn thủ công + Banner mời gọi nhẹ nhàng  |  Không bao giờ bị giới hạn
