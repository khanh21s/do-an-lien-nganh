#!/usr/bin/env python3
"""Extract and topic-tag headwords from the user-provided Oxford 3000 PDF.

Usage: python3 scripts/import_oxford3000.py /path/to/The_Oxford_3000_by_CEFR_level.pdf
Writes data/vocab/oxford3000-topics.tsv. The PDF supplies headwords, CEFR levels,
and parts of speech only; meaning/example columns are intentionally blank and must
be completed before these entries can be loaded as study cards.
"""
import csv
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data/vocab/oxford3000-topics.tsv"
LEVELS = {"A1", "A2", "B1", "B2"}
POS = ("modal v\\.", "auxiliary v\\.", "indefinite article", "to-infinitive",
       "number", "exclam\\.", "pron\\.", "conj\\.", "prep\\.", "det\\.",
       "adv\\.", "adj\\.", "v\\.", "n\\.")
POS_RE = re.compile(r"(?<![A-Za-z])(" + "|".join(POS) + r")")

# A conservative first-pass taxonomy. Ambiguous/general words remain in General English.
TOPICS = [
    ("People & relationships", "Người & các mối quan hệ", set("adult actor actress baby boy boyfriend brother child daughter father girl girlfriend husband mother mum dad aunt cousin family friend grandfather grandmother grandparent parent sister son uncle wife woman man people person teenager guest neighbor colleague".split())),
    ("Home & daily life", "Nhà cửa & sinh hoạt", set("apartment bathroom bedroom bed building chair clock clothes coat desk door dress floor home house kitchen lamp room table bathroom garden clean cook cooking wash".split())),
    ("Food & drink", "Đồ ăn & thức uống", set("apple banana beef beer bread breakfast butter cake carrot cheese chicken chocolate coffee cream dinner dish drink egg fish food fruit juice lunch meal meat milk potato rice salad sandwich tea tomato vegetable water".split())),
    ("Health & body", "Sức khỏe & cơ thể", set("arm back body cold cough doctor ear eye face feel feeling foot hair hand head health healthy heart hospital leg medicine mouth neck nose pain sick stomach tooth".split())),
    ("Education & language", "Học tập & ngôn ngữ", set("answer article class classroom college course dictionary education English exam example explain grammar history homework learn lesson library listen meaning school speak student study teach teacher test university word write".split())),
    ("Work & business", "Công việc & kinh doanh", set("accountant boss business career company customer employee job manager meeting office money price product service shop sell sale staff work worker".split())),
    ("Travel & transport", "Du lịch & giao thông", set("airport arrive bicycle bike boat bus drive driver flight journey map plane ride road station taxi train travel trip vehicle walk".split())),
    ("Nature & environment", "Thiên nhiên & môi trường", set("animal autumn beach bird flower forest farm farmer field fire fish island lake land mountain natural nature ocean plant river sea sky tree weather wind".split())),
    ("Arts, media & entertainment", "Nghệ thuật, truyền thông & giải trí", set("art artist band cinema dance dancer film festival game guitar movie music photograph picture play radio song television theater video".split())),
    ("Science & technology", "Khoa học & công nghệ", set("app camera computer data digital email internet machine mobile phone program screen software technology telephone website".split())),
    ("Society, culture & places", "Xã hội, văn hóa & địa điểm", set("area capital city country culture town village world language religion".split())),
    ("Time, numbers & measurement", "Thời gian, số & đơn vị đo", set("April August calendar century date day December February Friday hour January July June March May Monday minute month morning night number October second September time Tuesday Wednesday week weekend year".split())),
    ("Feelings & ideas", "Cảm xúc & ý tưởng", set("afraid amazing angry awful beautiful bored boring excited exciting famous fantastic fun glad good great happy interesting kind love lovely nervous nice popular sorry surprise surprised wonderful".split())),
]

MEANING_HINTS = [
    ("Food & drink", "Đồ ăn & thức uống", ("đồ ăn", "thức ăn", "thực phẩm", "thức uống", "ăn uống", "món ăn", "bữa ăn", "nước ép", "cà phê", "sữa", "thịt", "rau", "trái cây", "bánh", "trứng", "nấu ăn", "rượu", "bia", "đói", "khát")),
    ("Health & body", "Sức khỏe & cơ thể", ("sức khỏe", "bệnh viện", "bệnh nhân", "bệnh tật", "bị bệnh", "điều trị", "chữa bệnh", "thuốc", "đau đầu", "cơ thể", "tim mạch", "nhịp tim", "cánh tay", "cánh chân", "bàn tay", "bàn chân", "ngón tay", "ngón chân", "răng", "mắt", "tai", "mũi", "miệng", "bụng", "máu", "da", "phổi", "xương", "cổ họng", "sốt", "ho", "khỏe mạnh", "bác sĩ", "nha sĩ", "y tá", "phẫu thuật", "chấn thương")),
    ("Travel & transport", "Du lịch & giao thông", ("du lịch", "chuyến đi", "đi lại", "phương tiện", "giao thông", "sân bay", "máy bay", "xe buýt", "xe hơi", "xe đạp", "xe máy", "tàu hỏa", "xe lửa", "tàu thủy", "ga tàu", "nhà ga", "hành lý", "hộ chiếu", "khách sạn", "đặt vé", "vé xe", "vé tàu", "con đường", "đường phố", "đi bộ", "lái xe", "tài xế", "hành khách", "đến nơi", "khởi hành", "phương hướng", "bến xe", "bến cảng")),
    ("Home & daily life", "Nhà cửa & sinh hoạt", ("căn hộ", "phòng ngủ", "phòng tắm", "phòng khách", "phòng bếp", "phòng ăn", "đồ nội thất", "đồ đạc", "giường", "bàn ghế", "cái ghế", "cái bàn", "cánh cửa", "cửa sổ", "nhà bếp", "phòng", "nhà ở", "vòi nước", "tủ lạnh", "máy giặt", "lò vi sóng", "đèn", "sàn nhà", "mái nhà", "bồn tắm", "nhà vệ sinh", "cốc", "ly", "đĩa", "bát", "dao", "nĩa", "thìa", "chăn", "gối", "gương", "khăn")),
    ("Education & language", "Học tập & ngôn ngữ", ("giáo dục", "trường học", "trường đại học", "bài học", "học sinh", "sinh viên", "giáo viên", "giảng viên", "lớp học", "lớp", "thi cử", "kỳ thi", "bài kiểm tra", "từ vựng", "ngôn ngữ", "tiếng anh", "ngữ pháp", "phát âm", "đọc hiểu", "bài tập về nhà", "thư viện", "sách giáo khoa", "bằng cấp", "học tập", "đào tạo", "học bổng", "đề thi", "viết luận", "đọc sách")),
    ("Work & business", "Công việc & kinh doanh", ("công việc", "việc làm", "làm việc", "nghề nghiệp", "công ty", "doanh nghiệp", "kinh doanh", "văn phòng", "cuộc họp", "phòng họp", "nhân viên", "đồng nghiệp", "ông chủ", "quản lý", "giám đốc", "tiền lương", "tiền công", "hợp đồng", "thất nghiệp", "tuyển dụng", "phỏng vấn xin việc", "thăng chức", "báo cáo", "thương mại", "sản xuất", "nhà máy", "khách hàng", "đơn hàng", "nhà cung cấp", "lợi nhuận", "doanh thu", "ngân sách", "tiếp thị", "quảng cáo", "cửa hàng", "bán hàng", "mua bán", "cuộc hẹn", "lịch trình", "dự án")),
    ("Money & shopping", "Tiền bạc & mua sắm", ("tiền bạc", "tiền tệ", "ngân hàng", "tài khoản", "hóa đơn", "giá cả", "chi phí", "thanh toán", "trả tiền", "tiền mặt", "thẻ tín dụng", "tiết kiệm", "khoản vay", "nợ", "thuế", "lợi nhuận", "mua sắm", "mua hàng", "bán hàng", "giảm giá", "đắt tiền", "rẻ tiền", "ví tiền", "tiền lương", "tiền công", "đồng xu", "tiền giấy", "thẻ ngân hàng")),
    ("Nature & environment", "Thiên nhiên & môi trường", ("môi trường", "thiên nhiên", "thời tiết", "khí hậu", "động vật", "thực vật", "cây cối", "hoa", "rừng", "núi", "sông", "biển", "đại dương", "hồ nước", "sa mạc", "đảo", "đất đai", "bầu trời", "mưa", "gió", "tuyết", "bão", "ánh nắng", "ánh sáng mặt trời", "ô nhiễm", "năng lượng", "tài nguyên", "loài", "côn trùng", "chim", "thú", "nông trại", "nông nghiệp", "mùa", "động đất", "lũ lụt", "núi lửa")),
    ("Science & technology", "Khoa học & công nghệ", ("khoa học", "công nghệ", "máy tính", "máy vi tính", "điện thoại", "phần mềm", "internet", "trực tuyến", "mạng máy tính", "trang web", "dữ liệu", "thiết bị", "điện tử", "kỹ thuật", "kỹ thuật số", "ứng dụng", "màn hình", "bàn phím", "máy ảnh", "máy móc", "robot", "vũ trụ", "thí nghiệm", "phòng thí nghiệm", "nghiên cứu khoa học", "phát minh", "sáng chế", "pin", "năng lượng điện")),
    ("Arts, media & entertainment", "Nghệ thuật, truyền thông & giải trí", ("nghệ thuật", "âm nhạc", "bài hát", "ca hát", "nhạc sĩ", "ca sĩ", "phim ảnh", "bộ phim", "diễn viên", "nhà hát", "sân khấu", "truyền hình", "đài phát thanh", "chương trình truyền hình", "giải trí", "buổi hòa nhạc", "nhạc cụ", "đàn guitar", "đàn piano", "vẽ tranh", "họa sĩ", "bức tranh", "nhiếp ảnh", "ảnh chụp", "bảo tàng", "triển lãm", "truyện", "tiểu thuyết", "nhà văn", "thơ", "bài thơ", "khiêu vũ", "trò chơi điện tử")),
    ("Sports & games", "Thể thao & trò chơi", ("thể thao", "bóng đá", "bóng rổ", "quần vợt", "cầu lông", "bơi lội", "vận động viên", "trận đấu", "cuộc thi", "thi đấu", "đội bóng", "đội tuyển", "trọng tài", "sân vận động", "huy chương", "chiến thắng", "thua cuộc", "cờ vua", "trò chơi", "đồ chơi", "tập thể dục", "thể dục", "phòng tập", "giải đấu")),
    ("People & relationships", "Người & các mối quan hệ", ("gia đình", "bạn bè", "bạn thân", "người đàn ông", "người phụ nữ", "đàn ông", "phụ nữ", "trẻ em", "trẻ con", "con trai", "con gái", "cha mẹ", "bố mẹ", "người lớn", "người già", "vợ chồng", "anh em", "chị em", "họ hàng", "người thân", "quan hệ", "mối quan hệ", "hôn nhân", "đám cưới", "ly hôn", "hàng xóm", "bạn cùng lớp", "bạn học", "đồng nghiệp", "cư dân", "công dân", "khách hàng", "người lạ", "em bé", "đứa trẻ", "cặp đôi", "nhân loại", "con người")),
    ("Feelings & ideas", "Cảm xúc & ý tưởng", ("cảm xúc", "cảm giác", "cảm thấy", "hạnh phúc", "vui vẻ", "buồn bã", "tức giận", "giận dữ", "lo lắng", "sợ hãi", "ngạc nhiên", "thất vọng", "hào hứng", "chán nản", "yêu thích", "ghét", "hy vọng", "niềm tin", "ý tưởng", "suy nghĩ", "ý kiến", "quan điểm", "ước mơ", "trí nhớ", "ký ức", "tâm trí", "trí tuệ", "thông minh", "kiên nhẫn", "tự tin", "xấu hổ", "vui mừng", "đau buồn", "kinh ngạc")),
    ("Society, culture & places", "Xã hội, văn hóa & địa điểm", ("xã hội", "văn hóa", "chính phủ", "chính trị", "chính quyền", "luật pháp", "pháp luật", "tòa án", "tội phạm", "cảnh sát", "quốc gia", "thành phố", "thị trấn", "làng", "thủ đô", "cộng đồng", "tôn giáo", "lịch sử", "chiến tranh", "hòa bình", "tổ chức", "chính sách", "bầu cử", "bầu cử", "dân số", "đất nước", "quốc tế", "công chúng", "truyền thống", "phong tục", "ngôn ngữ", "dân tộc", "quyền lợi", "quyền", "tự do")),
    ("Time, numbers & measurement", "Thời gian, số & đơn vị đo", ("thời gian", "ngày tháng", "lịch", "thứ hai", "thứ ba", "thứ tư", "thứ năm", "thứ sáu", "thứ bảy", "chủ nhật", "tháng", "năm", "tuần", "ngày", "giờ", "phút", "giây", "thế kỷ", "lịch trình", "đồng hồ", "con số", "số lượng", "phần trăm", "kilômét", "mét", "độ dài", "trọng lượng", "chiều cao", "đo lường", "đơn vị đo", "nhiệt độ", "khoảng cách", "tuổi", "sinh nhật", "thời hạn")),
    ("Communication & information", "Giao tiếp & thông tin", ("thông tin", "tin tức", "thông báo", "tin nhắn", "điện thoại", "gọi điện", "trò chuyện", "nói chuyện", "cuộc trò chuyện", "cuộc hội thoại", "trao đổi", "giao tiếp", "trả lời", "câu trả lời", "câu hỏi", "hỏi han", "thảo luận", "tranh luận", "thư từ", "bức thư", "email", "thư điện tử", "báo chí", "tờ báo", "tạp chí", "phỏng vấn", "diễn đạt", "phát biểu", "ngôn ngữ", "giọng nói", "lời nói", "lời khuyên", "giải thích", "đề nghị", "đề xuất")),
]

def topic(word, meaning="", part_of_speech=""):
    key = re.sub(r"[^a-z ]", "", word.lower()).strip()
    first = key.split()[0] if key else ""
    for slug, label, words in TOPICS:
        if key in words or first in words:
            return slug, label
    normalized_meaning = (meaning or "").lower()
    for slug, label, hints in MEANING_HINTS:
        if any(hint in normalized_meaning for hint in hints):
            return slug, label
    pos = (part_of_speech or "").lower()
    if "number" in pos:
        return "Numbers & quantities", "Số & định lượng"
    if any(x in pos for x in ("pron", "det", "prep", "conj", "modal", "auxiliary", "exclam")):
        return "Grammar & connectors", "Ngữ pháp & từ nối"
    if "v." in pos or pos == "v":
        return "Actions & processes", "Hành động & quá trình"
    if "adj." in pos or "adv." in pos or pos in {"adj", "adv"}:
        return "Descriptions & qualities", "Miêu tả & tính chất"
    if "n." in pos or pos == "n":
        return "General nouns & concepts", "Danh từ & khái niệm"
    return "General English", "Tiếng Anh tổng quát"

def parse(path):
    text = subprocess.check_output(["pdftotext", "-raw", str(path), "-"], text=True)
    level, rows, skipped = None, [], 0
    for line in text.splitlines():
        line = line.strip()
        if line in LEVELS:
            level = line
            continue
        if not level or not line or line.startswith("The Oxford") or line.startswith("©") or re.match(r"^\d+\s*/\s*12$", line):
            continue
        matches = list(POS_RE.finditer(line))
        found = None
        for m in matches:
            head = line[:m.start()].strip()
            suffix = line[m.start():].strip()
            residue = POS_RE.sub("", suffix)
            if head and not re.search(r"[A-Za-z]", residue.replace("also", "").replace("auxiliary", "")):
                found = (head.rstrip(" ,"), suffix)
                break
        if not found:
            skipped += 1
            continue
        word, pos = found
        rows.append((word, pos, level, *topic(word)))
    return rows, skipped

def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__.splitlines()[2])
    rows, skipped = parse(Path(sys.argv[1]))
    # Keep the first appearance of a headword/level, preserving Oxford's level assignment.
    unique, seen = [], set()
    for row in rows:
        key = (row[0].casefold(), row[2])
        if key not in seen:
            seen.add(key)
            unique.append(row)
    with OUT.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(["word", "part_of_speech", "ipa", "cefr", "topic", "topic_vi", "meaning_vi", "example_en", "example_vi"])
        for word, pos, cefr, slug, label in unique:
            w.writerow([word, pos, "", cefr, slug, label, "", "", ""])
    print(f"Wrote {len(unique)} entries ({len(rows)-len(unique)} duplicate level/headword rows removed); skipped {skipped} non-entry lines.")

if __name__ == "__main__":
    main()
