# Prompts để Hoàn Thành Backend Parroto MVP

## I. SETUP KIẾN TRÚC & DATABASE

### Prompt 1: Khởi tạo dự án Spring Boot Microservices
```
Hãy tạo một Spring Boot project với cấu trúc microservices cho Parroto:
- Auth Service (port 8001): Xử lý đăng ký, đăng nhập, JWT token
- Content Service (port 8002): Quản lý bài học, sentence, metadata
- Practice Service (port 8003): Xử lý Dictation, Shadowing, lưu kết quả
- Vocabulary Service (port 8004): Quản lý bộ từ vựng, thẻ từ, SRS
- Subscription Service (port 8005): Quản lý gói, thanh toán, mã kích hoạt

Yêu cầu:
- Sử dụng Spring Boot 3.x + Spring Cloud Config
- Maven build
- Separate database cho mỗi service (Database per Service pattern)
- Common libraries: Lombok, MapStruct
- Error handling & logging unified
- Health check endpoints
```

### Prompt 2: Schema Database PostgreSQL
```
Hãy tạo SQL schema hoàn chỉnh cho Parroto gồm:

1. USER table:
   - id, email, passwordHash (null nếu Google/Apple), authProvider, cefrLevel, 
   - totalHoursLearned, totalWordsLearned, avatar, createdAt, updatedAt, isActive

2. LESSON table:
   - id, title, sourceUrl, cefrLevel, topicTag, audioUrl, description, 
   - duration, createdAt, updatedAt

3. SENTENCE table:
   - id, lessonId, text, ipa, translationVi, startMs, endMs, audioUrl, imageUrl

4. PRACTICE_ATTEMPT table:
   - id, userId, lessonId, mode (dictation/shadowing), score, accuracy, 
   - wordScores (jsonb), audioRecordingUrl (cho shadowing), duration, createdAt

5. VOCAB_DECK table:
   - id, name, description, level, isProOnly, createdBy (admin), createdAt, updatedAt

6. VOCAB_CARD table:
   - id, deckId, word, meaning, example, ipa, audioUrl, imageUrl, createdAt

7. USER_VOCAB table:
   - id, userId, cardId, nextReviewAt, intervalDays, easeFactor, repetitions, 
   - lastReviewAt, quality (0-5, SM-2), createdAt, updatedAt

8. SUBSCRIPTION table:
   - id, userId, planId, startAt, endAt, status (active/expired/cancelled), 
   - paymentMethod, transactionId, createdAt, updatedAt

9. PLAN table:
   - id, name, durationDays, price (VND), features (jsonb), createdAt

10. ACTIVATION_CODE table:
    - id, code (unique), planId, usedByUserId, usedAt, expiresAt, createdAt

11. PAYMENT_TRANSACTION table:
    - id, userId, amount, method (vnpay/momo), status (pending/success/failed), 
    - transactionId, description, createdAt

Yêu cầu:
- Primary key, foreign key constraints
- Indexes trên các trường search/sort: email, userId, lessonId, nextReviewAt, endAt
- Timestampz cho createdAt/updatedAt (UTC)
- Triggers để auto-update updatedAt
- Mô tả constraints business logic (ví dụ: endAt > startAt, price >= 0)
```

---

## II. AUTH SERVICE (Port 8001)

### Prompt 3: Auth Service - Registration & Login
```
Hãy xây dựng Auth Service cho Parroto:

Endpoints:
1. POST /auth/register
   - Body: email, password, cefrLevel (A1-C2)
   - Validations: email format, password length >= 8, CEFR level hợp lệ
   - Hash password với BCrypt (strength 12)
   - Tạo USER record (authProvider = 'email')
   - Response: userId, email, token (JWT), expiresIn
   - Return 409 nếu email đã tồn tại

2. POST /auth/login
   - Body: email, password
   - So sánh password với hash
   - Response: userId, email, token, expiresIn, cefrLevel
   - Return 401 nếu email/password sai

3. POST /auth/login-google
   - Body: googleToken (từ frontend)
   - Verify token với Google API
   - Tìm user theo email, nếu không có → tạo mới (authProvider = 'google')
   - Response: userId, email, token, cefrLevel
   - Hàng đợi: implement Google OAuth flow

4. POST /auth/login-apple
   - Tương tự Google, verify với Apple ID tokens

5. POST /auth/refresh-token
   - Body: refreshToken
   - Tạo token mới, response token mới

6. POST /auth/logout
   - Blacklist JWT token (lưu vào Redis, TTL = expiresIn)

JWT Payload:
```json
{
  "sub": "userId",
  "email": "user@example.com",
  "cefrLevel": "B1",
  "iat": timestamp,
  "exp": timestamp,
  "isPremium": boolean
}
```

Yêu cầu:
- Spring Security + JWT (jjwt library)
- JWT secret từ environment variables
- Refresh token rotation (each refresh tạo cặp token mới)
- Logout blacklist dùng Redis (key = "token_blacklist:{jti}", TTL = expiresIn)
- Email validation + uniqueness check
- Return error messages rõ ràng (email exists, invalid password format, etc.)
```

### Prompt 4: Auth Service - User Profile Management
```
Hãy thêm endpoints quản lý profile user vào Auth Service:

1. GET /auth/profile
   - Require: JWT token
   - Response: userId, email, cefrLevel, totalHoursLearned, totalWordsLearned, 
     avatar, isPremium, createdAt, updatedAt

2. PUT /auth/profile
   - Body: cefrLevel, avatar (URL hoặc base64), thông tin khác
   - Update USER table
   - Response: updated profile
   - Validation: cefrLevel in (A1, A2, B1, B2, C1, C2)

3. PUT /auth/change-password
   - Require: JWT token
   - Body: currentPassword, newPassword
   - Verify currentPassword
   - Hash newPassword, update DATABASE
   - Response: success message
   - Validation: newPassword length >= 8, không giống currentPassword

4. DELETE /auth/account
   - Require: JWT token
   - Soft delete: set isActive = false, không xóa dữ liệu
   - Response: success message

Yêu cầu:
- Validate JWT từ Authorization header (Bearer {token})
- Extract userId từ token, verify chủ sở hữu resource
- Update timestamp createdAt → không thay đổi, updatedAt → now()
- Không trả về passwordHash trong response
- Avatar upload: giới hạn size 2MB, định dạng jpg/png/webp
```

---

## III. CONTENT SERVICE (Port 8002)

### Prompt 5: Content Service - Lesson Management
```
Hãy xây dựng Content Service để quản lý bài học:

Endpoints:

1. GET /lessons
   - Query: cefrLevel (optional), topicTag (optional), page (default 1), limit (default 20)
   - Response: [{ id, title, sourceUrl, cefrLevel, topicTag, description, 
     duration, durationMinutes, sentenceCount, createdAt }, ...]
   - Pagination: offset = (page - 1) * limit
   - Filters: WHERE cefrLevel = ? AND topicTag LIKE ?
   - Sorting: ORDER BY createdAt DESC

2. GET /lessons/{id}
   - Response: lesson + sentences array
   - Sentences: [{ id, text, ipa, translationVi, startMs, endMs, audioUrl }, ...]
   - Include: lessonId, title, audioUrl (main audio), description

3. POST /lessons (Admin only)
   - Require: JWT + isPremium (hoặc admin role)
   - Body: title, sourceUrl, cefrLevel, topicTag, description, audioUrl
   - Validation: required fields, cefrLevel valid, audioUrl reachable (HEAD request)
   - Create LESSON record
   - Response: created lesson object

4. POST /lessons/{id}/sentences (Admin)
   - Body: sentences array = [{ text, ipa, translationVi, startMs, endMs, audioUrl }, ...]
   - Validation: startMs < endMs, startMs >= 0
   - Create SENTENCE records in batch
   - Response: created sentences

5. PUT /lessons/{id} (Admin)
   - Body: title, description, topicTag, cefrLevel
   - Update LESSON
   - Response: updated lesson

6. DELETE /lessons/{id} (Admin)
   - Soft delete: set updatedAt, mark as inactive (thêm column isDeleted)
   - Response: success

7. GET /lessons/search
   - Query: q (fulltext search), limit, offset
   - Search in: lesson.title, sentence.text, topic tags
   - Use PostgreSQL FULL TEXT SEARCH hoặc ILIKE
   - Response: [{ id, title, matchedSentences }, ...]

Yêu cầu:
- Content Service không phụ thuộc vào User db (independent database)
- Cache GET /lessons bằng Redis (TTL = 1 hour)
- Invalid cache khi POST/PUT/DELETE lessons
- Streaming response cho lessons với sentences lớn (chunking)
- Pagination để tránh loading quá nhiều data
```

### Prompt 6: Content Service - Audio/Video Management
```
Hãy thêm endpoints xử lý audio/video cho Content Service:

1. GET /lessons/{id}/audio
   - Stream audio file từ object storage (S3 hoặc Azure Blob)
   - Header: Content-Type: audio/mpeg, Content-Length
   - Support range requests (để seek khi nghe)
   - Cache-Control: public, max-age=86400

2. GET /lessons/{id}/audio/download
   - Download toàn bộ audio file

3. POST /lessons/{id}/upload-audio (Admin)
   - Multipart form: file (audio/mpeg), duration (seconds)
   - Upload lên object storage
   - Update LESSON.audioUrl
   - Response: audioUrl, duration

4. GET /sentences/{id}/audio
   - Stream audio for sentence (nếu có audioUrl riêng)

Yêu cầu:
- Integrate với S3 SDK (AWS SDK for Java) hoặc Azure Blob Storage
- Cấu hình S3 bucket: public read, CORS cho frontend
- Pre-signed URLs cho private audio (video premium)
- Cleanup: xóa audio từ storage khi delete lesson
- Audio format: MP3 (128kbps), duration tối đa 30 seconds per sentence
- CDN: CloudFront hoặc Azure CDN để serve audio files
```

---

## IV. PRACTICE SERVICE (Port 8003)

### Prompt 7: Practice Service - Dictation
```
Hãy xây dựng Practice Service cho Dictation:

Endpoints:

1. POST /practice/dictation/attempt
   - Require: JWT token
   - Body: lessonId, sentenceId, userInput (text), difficulty (Easy/Normal/Hard)
   - Validations: 
     * userId from token
     * lesson & sentence tồn tại (validate với Content Service)
     * userInput không empty
   
   - Logic so sánh:
     * Normalize text: lowercase, remove punctuation & extra spaces
     * Split thành words
     * So sánh word-by-word với transcript
     * Tính accuracy = (correctWords / totalWords) * 100
     * Tính score dựa vào accuracy + difficulty:
       - Easy: score = accuracy
       - Normal: score = accuracy * 1.2
       - Hard: score = accuracy * 1.5
     * Tạo wordScores array: [{word, isCorrect, position}, ...]
     * Lưu PRACTICE_ATTEMPT record
   
   - Response:
   ```json
   {
     "attemptId": "uuid",
     "lessonId": "uuid",
     "sentenceId": "uuid",
     "accuracy": 85.5,
     "score": 102.6,
     "totalWords": 12,
     "correctWords": 10,
     "wordScores": [
       {"position": 0, "word": "hello", "isCorrect": true},
       {"position": 1, "word": "world", "isCorrect": false, "expected": "world"}
     ],
     "createdAt": "2024-01-15T10:30:00Z"
   }
   ```

2. GET /practice/attempts
   - Query: lessonId (optional), mode (optional), limit, page
   - Response: [attemptId, lessonId, mode, score, accuracy, createdAt, ...]
   - Pagination

3. GET /practice/attempts/{id}
   - Response: full attempt detail (gồm wordScores)

4. GET /practice/lessons/{lessonId}/attempts
   - Get tất cả attempts của user cho 1 lesson
   - Response: [attempts], plus summary (avg accuracy, total attempts)

Yêu cầu:
- Word matching algorithm: levenshtein distance (nếu cần fuzzy match)
- Lưu word-level scores để hiển thị feedback
- Tính streak: GET /practice/streak trả về currentStreak, lastPracticeDate
- Streak reset nếu 1 ngày không practice
```

### Prompt 8: Practice Service - Shadowing & Audio Processing
```
Hãy xây dựng endpoints cho Shadowing (ghi âm nhại):

1. POST /practice/shadowing/request-upload-url
   - Require: JWT token
   - Body: lessonId, sentenceId
   - Logic: Tạo presigned URL cho upload audio từ frontend
   - Response:
   ```json
   {
     "uploadUrl": "https://s3.amazonaws.com/...",
     "recordingId": "uuid",
     "expiresIn": 3600
   }
   ```

2. POST /practice/shadowing/attempt
   - Require: JWT token
   - Body: lessonId, sentenceId, recordingId, duration
   - Logic:
     * Lấy audio file từ recordingId (S3)
     * Gọi Azure Pronunciation Assessment API để chấm phát âm
     * Xử lý response từ API
     * Tạo wordScores array từ phoneme-level scores
     * Lưu PRACTICE_ATTEMPT record (mode = 'shadowing')
   
   - Response:
   ```json
   {
     "attemptId": "uuid",
     "recordingUrl": "https://s3.../recording.mp3",
     "score": 78.5,
     "accuracy": 78.5,
     "duration": 5.2,
     "wordScores": [
       {
         "word": "hello",
         "phonemes": [
           {"phoneme": "h", "accuracy": 95},
           {"phoneme": "ə", "accuracy": 88},
           {"phoneme": "l", "accuracy": 92},
           {"phoneme": "oʊ", "accuracy": 75}
         ],
         "wordAccuracy": 87.5,
         "status": "yellow" // red|yellow|green
       }
     ],
     "createdAt": "2024-01-15T10:30:00Z"
   }
   ```

3. GET /practice/shadowing/{attemptId}/recording
   - Stream audio recording từ S3
   - Header: Content-Type: audio/mpeg

Azure Pronunciation Assessment Integration:
```
POST https://[region].tts.speech.microsoft.com/cognitiveservices/v1
Headers:
  Ocp-Apim-Subscription-Key: {key}
  Content-Type: audio/mpeg
Body: binary audio data

Response:
{
  "NBest": [{
    "Confidence": 0.85,
    "Words": [
      {
        "Word": "hello",
        "Accuracy": 0.85,
        "ErrorType": "None",
        "Phonemes": [...]
      }
    ]
  }]
}
```

Yêu cầu:
- Implement Azure SDK: com.microsoft.cognitiveservices.speech
- Error handling: audio file corrupted, API timeout
- Cleanup: xóa recording audio từ S3 sau 30 ngày
- Caching: cache Azure API responses 1 hour (same audio → same score)
- Async processing: có thể làm async + webhook callback nếu API slow
```

### Prompt 9: Practice Service - Statistics & Streaks
```
Hãy thêm endpoints tính statistics cho Practice Service:

1. GET /practice/stats
   - Require: JWT token
   - Response:
   ```json
   {
     "totalAttempts": 45,
     "totalHoursLearned": 3.5,
     "dictationAccuracy": 82.3,
     "shadowingAccuracy": 75.4,
     "currentStreak": 7,
     "longestStreak": 14,
     "lastPracticeDate": "2024-01-15T10:30:00Z",
     "lessonsCompleted": 12,
     "averageScore": 85.2,
     "accuracy": {
       "weekly": [78, 80, 82, 85, 83, 81, 84],
       "dates": ["2024-01-09", "2024-01-10", ..., "2024-01-15"]
     }
   }
   ```

2. GET /practice/stats/by-lesson/{lessonId}
   - Stats cho 1 lesson cụ thể (user's performance)
   - Response: attempts, avg accuracy, best score, last attempt

3. GET /practice/stats/trending-errors
   - Return từ/câu mà user hay sai (accuracy < 70%)
   - Sorted by frequency
   - Response: [{ word, count, avgAccuracy }, ...]

4. POST /practice/calculate-streak (internal, called after each attempt)
   - Logic:
     * Nếu lastPracticeDate hôm nay → currentStreak = currentStreak
     * Nếu lastPracticeDate hôm qua → currentStreak = currentStreak + 1
     * Nếu lastPracticeDate > 2 ngày trước → currentStreak = 1
     * Update USER.totalHoursLearned += duration

Yêu cầu:
- Cache stats bằng Redis (TTL = 1 hour), invalidate khi có attempt mới
- Tính từ PRACTICE_ATTEMPT table (không duplicate queries)
- Performance: use aggregation queries hoặc pre-calculate daily stats
- Weekly/monthly views: denormalize vào stats cache
```

---

## V. VOCABULARY SERVICE (Port 8004)

### Prompt 10: Vocabulary Service - Deck & Card Management
```
Hãy xây dựng Vocabulary Service để quản lý từ vựng:

Endpoints:

1. GET /vocabs/decks
   - Query: level (optional), isProOnly (optional), page, limit
   - Response:
   ```json
   {
     "data": [
       {
         "id": "uuid",
         "name": "Oxford 3000 - Level A1",
         "description": "...",
         "level": "A1",
         "isProOnly": false,
         "cardCount": 250,
         "createdAt": "2024-01-01T00:00:00Z"
       }
     ],
     "pagination": { "page": 1, "limit": 20, "total": 45 }
   }
   ```

2. GET /vocabs/decks/{id}/cards
   - Query: page, limit, searchWord (optional)
   - Response:
   ```json
   {
     "data": [
       {
         "id": "uuid",
         "word": "hello",
         "meaning": "tạm biệt / xin chào",
         "example": "Hello, how are you?",
         "ipa": "/həˈloʊ/",
         "audioUrl": "https://s3/.../hello.mp3",
         "imageUrl": "https://s3/.../hello.jpg"
       }
     ],
     "pagination": { ... }
   }
   ```

3. POST /vocabs/decks (Admin)
   - Body: name, description, level (A1-C2), isProOnly
   - Create VOCAB_DECK record
   - Response: created deck

4. POST /vocabs/decks/{id}/cards (Admin)
   - Body: cards array = [{ word, meaning, example, ipa, audioUrl, imageUrl }, ...]
   - Batch insert VOCAB_CARD
   - Validation: word không duplicate trong deck
   - Response: created cards

5. PUT /vocabs/cards/{id} (Admin)
   - Body: meaning, example, ipa, audioUrl, imageUrl
   - Update VOCAB_CARD
   - Response: updated card

6. DELETE /vocabs/decks/{id} (Admin)
   - Soft delete VOCAB_DECK + related VOCAB_CARDs
   - Response: success

Yêu cầu:
- Cache decks list (TTL = 24 hours)
- Cache individual deck cards (TTL = 24 hours)
- Implement fulltext search cho words (PostgreSQL ILIKE)
- Pre-load: 20+ decks (Oxford 3000, TOEIC, IELTS, etc.) có sẵn
```

### Prompt 11: Vocabulary Service - SRS Algorithm (SM-2)
```
Hãy implement Spaced Repetition System (SM-2) trong Vocabulary Service:

SM-2 Algorithm:
```
Dựa trên: https://en.wikipedia.org/wiki/Spaced_repetition#SM-2

Biến số:
- quality (0-5): user rating (0=forget, 5=easy)
- easeFactor (default 2.5): độ khó, range [1.3, 2.5]
- interval (days): khoảng cách ôn lại
- repetitions: số lần đã ôn

Công thức:
1. Nếu repetitions = 0:
   - interval = 1
2. Nếu repetitions = 1:
   - interval = 3
3. Nếu repetitions > 1:
   - interval = Math.round(interval * easeFactor)
   
Cập nhật easeFactor:
- newEaseFactor = easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
- Clamp: max(1.3, newEaseFactor)

nextReviewAt = now() + interval days
```

Endpoints:

1. GET /vocabs/user/review-queue
   - Require: JWT token
   - Response:
   ```json
   {
     "queue": [
       {
         "id": "uuid",
         "cardId": "uuid",
         "word": "hello",
         "meaning": "...",
         "example": "...",
         "ipa": "...",
         "lastReviewAt": "2024-01-10T00:00:00Z",
         "nextReviewAt": "2024-01-15T00:00:00Z",
         "repetitions": 3,
         "easeFactor": 2.4,
         "intervalDays": 4
       }
     ],
     "totalDueCards": 12,
     "totalCards": 250
   }
   ```
   - Query: only WHERE nextReviewAt <= now() & userId matches

2. POST /vocabs/user/review
   - Require: JWT token
   - Body: userVocabId, quality (0-5)
   - Logic:
     * Get current USER_VOCAB record
     * Tính new interval & easeFactor theo SM-2
     * Update USER_VOCAB:
       - nextReviewAt = now() + new interval
       - easeFactor = new easeFactor
       - repetitions += 1
       - lastReviewAt = now()
       - quality = quality (for reference)
     * Return updated USER_VOCAB
   
   - Response:
   ```json
   {
     "userVocabId": "uuid",
     "nextReviewAt": "2024-01-22T00:00:00Z",
     "easeFactor": 2.45,
     "interval": 7,
     "repetitions": 4,
     "message": "Tốt lắm! Ôn lại trong 7 ngày."
   }
   ```

3. GET /vocabs/user/stats
   - Require: JWT token
   - Response:
   ```json
   {
     "totalLearned": 250,
     "dueToday": 12,
     "dueThisWeek": 35,
     "bySRS": {
       "new": 50,
       "learning": 80,
       "review": 120
     },
     "accuracy": 0.85
   }
   ```

4. GET /vocabs/user/learned
   - List tất cả từ user đã học (dùng frontend để hiển thị sổ tay)
   - Query: page, limit, searchWord
   - Response: [{ cardId, word, meaning, repetitions, accuracy }, ...]

Yêu cầu:
- Validate quality parameter: 0-5, return 400 nếu invalid
- nextReviewAt always future time (không cho phép review card quá sớm)
- Add column để track quality rating (cho analytics)
- Optimize: use single update statement, tránh N+1 queries
```

### Prompt 12: Vocabulary Service - Add Word from Lesson
```
Hãy thêm endpoint để lưu từ từ khi học:

1. POST /vocabs/user/add-from-lesson
   - Require: JWT token
   - Body: cardId (từ VOCAB_CARD), source (lessonId hoặc deckId)
   - Logic:
     * Check if user_vocab record exists
     * Nếu có rồi: return 409 (conflict) "Từ đã trong sổ tay"
     * Nếu chưa: tạo USER_VOCAB record:
       - nextReviewAt = now() (ôn lại hôm nay)
       - intervalDays = 1
       - easeFactor = 2.5
       - repetitions = 0
       - lastReviewAt = NULL
     * Response: created USER_VOCAB
   
   - Response:
   ```json
   {
     "userVocabId": "uuid",
     "cardId": "uuid",
     "word": "hello",
     "meaning": "...",
     "nextReviewAt": "2024-01-15T00:00:00Z",
     "status": "new"
   }
   ```

2. DELETE /vocabs/user/{userVocabId}
   - Remove từ khỏi sổ tay

Yêu cầu:
- Tự động thêm word khi user chạm vào từ trong Dictation/Shadowing
- Hoặc cho phép user chủ động thêm từ từ UI
```

---

## VI. SUBSCRIPTION SERVICE (Port 8005)

### Prompt 13: Subscription Service - Plan & Billing
```
Hãy xây dựng Subscription Service để quản lý gói Premium:

Endpoints:

1. GET /subscriptions/plans
   - Response:
   ```json
   {
     "plans": [
       {
         "id": "plan-1month",
         "name": "Premium 1 tháng",
         "durationDays": 30,
         "price": 99000,
         "features": [
           "Không quảng cáo",
           "Tất cả bộ từ vựng Pro",
           "Sổ tay không giới hạn"
         ],
         "priceDisplay": "99.000 ₫"
       },
       {
         "id": "plan-1year",
         "name": "Premium 1 năm",
         "durationDays": 365,
         "price": 299000,
         "features": [...],
         "priceDisplay": "299.000 ₫"
       }
     ]
   }
   ```

2. GET /subscriptions/user
   - Require: JWT token
   - Response:
   ```json
   {
     "subscription": {
       "id": "sub-uuid",
       "planId": "plan-1year",
       "planName": "Premium 1 năm",
       "startAt": "2024-01-01T00:00:00Z",
       "endAt": "2025-01-01T00:00:00Z",
       "status": "active",
       "daysRemaining": 350,
       "autoRenew": false
     },
     "isPremium": true
   }
   ```

3. POST /subscriptions/create-payment
   - Require: JWT token
   - Body: planId
   - Logic:
     * Lấy plan info
     * Tạo PAYMENT_TRANSACTION record (status = 'pending')
     * Gọi VNPay API để lấy payment URL
     * Return payment URL cho frontend redirect
   
   - Response:
   ```json
   {
     "transactionId": "txn-uuid",
     "paymentUrl": "https://sandbox.vnpayment.vn/paypage/...",
     "expiresIn": 900,
     "amount": 99000
   }
   ```

4. GET /subscriptions/payment-callback
   - Webhook từ VNPay khi thanh toán xong
   - Query: vnp_TxnRef, vnp_Amount, vnp_ResponseCode, vnp_SecureHash
   - Logic:
     * Verify signature: hmacSHA512(queryString, VNPay secret key)
     * Nếu ResponseCode = '00' (success):
       - Lấy transactionId từ vnp_TxnRef
       - Update PAYMENT_TRANSACTION.status = 'success'
       - Lấy planId từ transaction
       - Tạo/Update SUBSCRIPTION record:
         * startAt = now()
         * endAt = now() + plan.durationDays
         * status = 'active'
     * Gửi email confirmation cho user
     * Redirect frontend về /subscription/success page

5. POST /subscriptions/activate-code
   - Require: JWT token
   - Body: code
   - Logic:
     * Tìm ACTIVATION_CODE record
     * Validation:
       - code tồn tại
       - usedByUserId = NULL (chưa dùng)
       - expiresAt > now()
     * Update ACTIVATION_CODE: usedByUserId = userId, usedAt = now()
     * Lấy planId từ code
     * Tạo/Update SUBSCRIPTION (như payment success)
     * Response: success, subscription details

6. GET /subscriptions/transactions
   - Require: JWT token
   - Response: [{ id, amount, method, status, createdAt, planName }, ...]
   - Pagination

Yêu cầu:
- VNPay Integration:
  - Development account: https://sandbox.vnpayment.vn
  - Production: https://vnpayment.vn
  - Environment variables: VNPay API Key, Hash Secret
  - Verify signature cẩn thận (security critical)
  
- Email notifications:
  - Payment success: send confirmation email
  - Subscription expiring soon (7 days before): reminder email
  
- Error handling:
  - Invalid plan ID → 404
  - User already premium → 409
  - Payment timeout → return specific error
```

### Prompt 14: Subscription Service - Premium Feature Authorization
```
Hãy thêm authorization checks cho Premium features:

1. Middleware: checkPremium
   - Extract userId từ JWT
   - Query SUBSCRIPTION table: WHERE userId = ? AND status = 'active' AND endAt > now()
   - Set req.isPremium = true/false
   - Nếu endpoint cần premium & !isPremium → return 403 "Premium required"

2. Endpoints cần premium check:
   - GET /vocabs/decks/{id}/cards (nếu deck.isProOnly = true)
   - GET /lessons/{id} (nếu lesson.isPremiumOnly = true)
   - POST /vocabs/user/add-deck (học bộ thẻ Pro)

3. Implement tại API Gateway (hoặc mỗi service):
```java
@Component
public class PremiumCheckFilter {
  public boolean isPremium(String userId) {
    return subscriptionService.hasActiveSubscription(userId);
  }
}
```

Yêu cầu:
- Cache premium status trong JWT token (isPremium field)
- Check expiration endAt khi verify token
- Invalidate cache khi subscription changes
```

---

## VII. API GATEWAY

### Prompt 15: API Gateway Configuration
```
Hãy setup API Gateway (Express.js hoặc Kong) cho Parroto:

Architecture:
```
Frontend (3000)
    ↓
API Gateway (8000)
    ├→ Auth Service (8001)
    ├→ Content Service (8002)
    ├→ Practice Service (8003)
    ├→ Vocabulary Service (8004)
    └→ Subscription Service (8005)
```

Responsibilities:
1. Request routing: /auth/* → 8001, /content/* → 8002, etc.
2. JWT validation: check Authorization header, verify token
3. Rate limiting: 1000 req/hour per IP, 5000 req/hour per user
4. CORS: Allow frontend origin
5. Request/response logging
6. Error handling & status code standardization
7. Request ID tracking (correlation ID)

Express.js Setup:
```javascript
const express = require('express');
const httpProxy = require('express-http-proxy');
const rateLimit = require('express-rate-limit');
const jwt = require('jsonwebtoken');

const app = express();

// Middleware
app.use(express.json());
app.use(cors({ origin: process.env.FRONTEND_URL }));
app.use(requestLogger);

// Rate limiting
const limiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 1000, // limit each IP to 1000 requests per windowMs
  message: 'Too many requests'
});
app.use(limiter);

// JWT validation middleware
const verifyToken = (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'No token' });
  
  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
    next();
  } catch (err) {
    res.status(401).json({ error: 'Invalid token' });
  }
};

// Routes
app.use('/auth', httpProxy('http://localhost:8001'));
app.use('/content', verifyToken, httpProxy('http://localhost:8002'));
app.use('/practice', verifyToken, httpProxy('http://localhost:8003'));
app.use('/vocabs', verifyToken, httpProxy('http://localhost:8004'));
app.use('/subscriptions', verifyToken, httpProxy('http://localhost:8005'));

// Health check
app.get('/health', (req, res) => res.json({ status: 'OK' }));

app.listen(8000, () => console.log('Gateway running on 8000'));
```

Yêu cầu:
- Public endpoints (/auth/register, /auth/login): không cần verifyToken
- Private endpoints: require verifyToken middleware
- Error responses standardized:
```json
{
  "error": "Error message",
  "status": 400,
  "timestamp": "2024-01-15T10:30:00Z",
  "requestId": "req-uuid"
}
```
```

---

## VIII. DOCKER & DEPLOYMENT

### Prompt 16: Dockerfile for Each Service
```
Hãy tạo Dockerfile cho mỗi microservice (Java Spring Boot):

Dockerfile template:
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Yêu cầu:
- Multi-stage build (giảm image size từ 1GB → 300MB)
- Alpine base image (nhẹ hơn)
- Expose port tương ứng (8001, 8002, ...)
- ENV variables: DATABASE_URL, JWT_SECRET, AZURE_KEY, etc.
- Health check: HEALTHCHECK CMD curl http://localhost:port/health

Dockerfile cho API Gateway (Node.js):
```dockerfile
FROM node:21-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --production
COPY . .
EXPOSE 8000
CMD ["node", "server.js"]
```

Yêu cầu:
- Use .dockerignore để exclude node_modules, logs
```

### Prompt 17: Docker Compose Configuration
```
Hãy tạo docker-compose.yml cho toàn bộ hệ thống Parroto:

docker-compose.yml:
```yaml
version: '3.9'

services:
  # Database
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: parroto
      POSTGRES_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      POSTGRES_DB: parroto_db
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U parroto"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis cache
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Auth Service
  auth-service:
    build:
      context: ./services/auth-service
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/auth_db
      DATABASE_USER: parroto
      DATABASE_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET:-dev_secret_key_change_in_prod}
      JWT_EXPIRY: 3600
      PORT: 8001
    ports:
      - "8001:8001"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8001/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Content Service
  content-service:
    build:
      context: ./services/content-service
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/content_db
      DATABASE_USER: parroto
      DATABASE_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      PORT: 8002
      S3_BUCKET: ${S3_BUCKET:-parroto-content}
      S3_REGION: ${S3_REGION:-us-east-1}
      AWS_ACCESS_KEY: ${AWS_ACCESS_KEY}
      AWS_SECRET_KEY: ${AWS_SECRET_KEY}
    ports:
      - "8002:8002"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  # Practice Service
  practice-service:
    build:
      context: ./services/practice-service
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/practice_db
      DATABASE_USER: parroto
      DATABASE_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      PORT: 8003
      AZURE_SPEECH_KEY: ${AZURE_SPEECH_KEY}
      AZURE_SPEECH_REGION: ${AZURE_SPEECH_REGION:-eastus}
      CONTENT_SERVICE_URL: http://content-service:8002
    ports:
      - "8003:8003"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  # Vocabulary Service
  vocabulary-service:
    build:
      context: ./services/vocabulary-service
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/vocabulary_db
      DATABASE_USER: parroto
      DATABASE_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      PORT: 8004
    ports:
      - "8004:8004"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  # Subscription Service
  subscription-service:
    build:
      context: ./services/subscription-service
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/subscription_db
      DATABASE_USER: parroto
      DATABASE_PASSWORD: ${DB_PASSWORD:-parroto_dev}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      PORT: 8005
      VNPAY_API_URL: ${VNPAY_API_URL:-https://sandbox.vnpayment.vn}
      VNPAY_API_KEY: ${VNPAY_API_KEY}
      VNPAY_SECRET: ${VNPAY_SECRET}
    ports:
      - "8005:8005"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  # API Gateway
  api-gateway:
    build:
      context: ./api-gateway
      dockerfile: Dockerfile
    environment:
      PORT: 8000
      JWT_SECRET: ${JWT_SECRET:-dev_secret_key_change_in_prod}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost:3000}
      AUTH_SERVICE_URL: http://auth-service:8001
      CONTENT_SERVICE_URL: http://content-service:8002
      PRACTICE_SERVICE_URL: http://practice-service:8003
      VOCABULARY_SERVICE_URL: http://vocabulary-service:8004
      SUBSCRIPTION_SERVICE_URL: http://subscription-service:8005
    ports:
      - "8000:8000"
    depends_on:
      - auth-service
      - content-service
      - practice-service
      - vocabulary-service
      - subscription-service

volumes:
  postgres_data:

networks:
  default:
    driver: bridge
```

.env file:
```
# Database
DB_PASSWORD=parroto_dev_password

# JWT
JWT_SECRET=your_super_secret_jwt_key_change_in_prod

# AWS S3
S3_BUCKET=parroto-dev-bucket
S3_REGION=us-east-1
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key

# Azure Speech
AZURE_SPEECH_KEY=your_azure_key
AZURE_SPEECH_REGION=eastus

# VNPay
VNPAY_API_URL=https://sandbox.vnpayment.vn
VNPAY_API_KEY=your_vnpay_key
VNPAY_SECRET=your_vnpay_secret

# Frontend
FRONTEND_URL=http://localhost:3000
```

Yêu cầu:
- Mỗi service có dependency trên PostgreSQL & Redis
- Health checks để tránh start service trước DB ready
- Volumes để persist PostgreSQL data
- Environment variables cho development & production (khác nhau)
- Networks: để services communicate với nhau

Commands:
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f api-gateway

# Stop services
docker-compose down

# Rebuild services
docker-compose up -d --build
```
```

### Prompt 18: Database Initialization Script
```
Hãy tạo init-db.sql để khởi tạo databases cho mỗi service:

init-db.sql:
```sql
-- Tạo users cho mỗi service
CREATE USER auth_user WITH PASSWORD 'auth_password';
CREATE USER content_user WITH PASSWORD 'content_password';
CREATE USER practice_user WITH PASSWORD 'practice_password';
CREATE USER vocabulary_user WITH PASSWORD 'vocabulary_password';
CREATE USER subscription_user WITH PASSWORD 'subscription_password';

-- Tạo databases
CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE content_db OWNER content_user;
CREATE DATABASE practice_db OWNER practice_user;
CREATE DATABASE vocabulary_db OWNER vocabulary_user;
CREATE DATABASE subscription_db OWNER subscription_user;

-- ===== AUTH_DB SCHEMA =====
\c auth_db auth_user

CREATE TABLE "user" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255),
  auth_provider VARCHAR(50) NOT NULL DEFAULT 'email', -- email|google|apple
  cefr_level VARCHAR(2) DEFAULT 'B1', -- A1|A2|B1|B2|C1|C2
  total_hours_learned DECIMAL DEFAULT 0,
  total_words_learned INTEGER DEFAULT 0,
  avatar_url TEXT,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_created_at ON "user"(created_at DESC);

-- ===== CONTENT_DB SCHEMA =====
\c content_db content_user

CREATE TABLE lesson (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  source_url TEXT,
  cefr_level VARCHAR(2),
  topic_tag VARCHAR(100),
  audio_url TEXT,
  description TEXT,
  duration INTEGER, -- seconds
  is_deleted BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sentence (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lesson_id UUID NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  ipa VARCHAR(255),
  translation_vi TEXT,
  start_ms INTEGER,
  end_ms INTEGER,
  audio_url TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lesson_cefr ON lesson(cefr_level);
CREATE INDEX idx_lesson_topic ON lesson(topic_tag);
CREATE INDEX idx_sentence_lesson ON sentence(lesson_id);

-- ===== PRACTICE_DB SCHEMA =====
\c practice_db practice_user

CREATE TABLE practice_attempt (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  lesson_id UUID NOT NULL,
  mode VARCHAR(20) NOT NULL, -- dictation|shadowing
  score DECIMAL,
  accuracy DECIMAL,
  word_scores JSONB,
  audio_recording_url TEXT,
  duration DECIMAL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_practice_user ON practice_attempt(user_id);
CREATE INDEX idx_practice_lesson ON practice_attempt(lesson_id);
CREATE INDEX idx_practice_created ON practice_attempt(created_at DESC);

-- ===== VOCABULARY_DB SCHEMA =====
\c vocabulary_db vocabulary_user

CREATE TABLE vocab_deck (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  level VARCHAR(2), -- A1-C2
  is_pro_only BOOLEAN DEFAULT false,
  created_by UUID,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vocab_card (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deck_id UUID NOT NULL REFERENCES vocab_deck(id) ON DELETE CASCADE,
  word VARCHAR(255) NOT NULL,
  meaning TEXT NOT NULL,
  example TEXT,
  ipa VARCHAR(255),
  audio_url TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_vocab (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  card_id UUID NOT NULL REFERENCES vocab_card(id) ON DELETE CASCADE,
  next_review_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  interval_days INTEGER DEFAULT 1,
  ease_factor DECIMAL DEFAULT 2.5,
  repetitions INTEGER DEFAULT 0,
  quality INTEGER, -- 0-5 (SM-2)
  last_review_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vocab_card_deck ON vocab_card(deck_id);
CREATE INDEX idx_user_vocab_user ON user_vocab(user_id);
CREATE INDEX idx_user_vocab_review ON user_vocab(user_id, next_review_at) WHERE next_review_at <= CURRENT_TIMESTAMP;

-- ===== SUBSCRIPTION_DB SCHEMA =====
\c subscription_db subscription_user

CREATE TABLE plan (
  id VARCHAR(50) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  duration_days INTEGER NOT NULL,
  price BIGINT NOT NULL, -- VND
  features JSONB,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscription (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  plan_id VARCHAR(50) NOT NULL REFERENCES plan(id),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) DEFAULT 'active', -- active|expired|cancelled
  payment_method VARCHAR(50),
  transaction_id VARCHAR(255),
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_transaction (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  plan_id VARCHAR(50),
  amount BIGINT NOT NULL,
  method VARCHAR(50), -- vnpay|momo
  status VARCHAR(20) DEFAULT 'pending', -- pending|success|failed
  transaction_id VARCHAR(255),
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activation_code (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(50) UNIQUE NOT NULL,
  plan_id VARCHAR(50) NOT NULL REFERENCES plan(id),
  used_by_user_id UUID,
  used_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subscription_user ON subscription(user_id);
CREATE INDEX idx_subscription_end ON subscription(end_at) WHERE status = 'active';
CREATE INDEX idx_activation_code ON activation_code(code);
CREATE INDEX idx_payment_user ON payment_transaction(user_id);

-- Insert mẫu plans
INSERT INTO plan (id, name, duration_days, price, features) VALUES
  ('plan-1month', '1 tháng', 30, 99000, '["Không quảng cáo", "Tất cả từ vựng Pro", "Sổ tay không giới hạn"]'::jsonb),
  ('plan-3month', '3 tháng', 90, 199000, '["Không quảng cáo", "Tất cả từ vựng Pro", "Sổ tay không giới hạn"]'::jsonb),
  ('plan-1year', '1 năm', 365, 299000, '["Không quảng cáo", "Tất cả từ vựng Pro", "Sổ tay không giới hạn"]'::jsonb);

-- Grant permissions
GRANT CONNECT ON DATABASE auth_db TO auth_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO auth_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO auth_user;

-- Tương tự cho các DB khác...
```

Yêu cầu:
- Script tự động chạy khi PostgreSQL start trong Docker
- Tạo indexes cho tối ưu query performance
- Triggers để auto-update updated_at
- Foreign keys để maintain data integrity
- Ngôn ngữ: Vietnamese comments & data
```

### Prompt 19: Environment Configuration & Secrets Management
```
Hãy tạo config cho development, staging, production:

Directory structure:
```
parroto-backend/
├── docker-compose.yml
├── docker-compose.prod.yml
├── .env.example
├── .env.dev
├── .env.staging
├── .env.prod (gitignore)
├── services/
│   ├── auth-service/
│   │   ├── src/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── pom.xml
│   └── ... (other services)
└── api-gateway/
    └── config/
        ├── dev.env
        ├── prod.env
        └── ...
```

.env.example (template):
```
# ==== DEVELOPMENT ONLY - CHANGE FOR PRODUCTION ====
ENVIRONMENT=development
LOG_LEVEL=debug

# Database
DB_HOST=postgres
DB_PORT=5432
DB_PASSWORD=parroto_dev_password

# JWT
JWT_SECRET=change_this_in_production_use_strong_random_key_here
JWT_EXPIRY=3600

# AWS S3
S3_BUCKET=parroto-dev-bucket
S3_REGION=us-east-1
AWS_ACCESS_KEY=your_dev_aws_key
AWS_SECRET_KEY=your_dev_aws_secret

# Azure Speech API
AZURE_SPEECH_KEY=your_dev_azure_key
AZURE_SPEECH_REGION=eastus

# VNPay
VNPAY_API_URL=https://sandbox.vnpayment.vn
VNPAY_API_KEY=your_dev_vnpay_key
VNPAY_SECRET=your_dev_vnpay_secret

# Frontend
FRONTEND_URL=http://localhost:3000

# Email notifications (optional, for later)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=noreply@parroto.com
SMTP_PASSWORD=your_smtp_password
```

.env.prod (secrets):
```
ENVIRONMENT=production
LOG_LEVEL=info

DB_PASSWORD=[PRODUCTION_DB_PASSWORD]
JWT_SECRET=[STRONG_RANDOM_SECRET_32_CHARS_MIN]
AWS_ACCESS_KEY=[PROD_AWS_KEY]
AWS_SECRET_KEY=[PROD_AWS_SECRET]
AZURE_SPEECH_KEY=[PROD_AZURE_KEY]
VNPAY_API_URL=https://vnpayment.vn
VNPAY_API_KEY=[PROD_VNPAY_KEY]
VNPAY_SECRET=[PROD_VNPAY_SECRET]
FRONTEND_URL=https://parroto.com
```

Spring Boot application.yml:
```yaml
spring:
  profiles:
    active: ${ENVIRONMENT:dev}

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/auth_db
    username: auth_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: ${SHOW_SQL:false}
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQL15Dialect

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

logging:
  level:
    root: ${LOG_LEVEL:info}
    com.parroto: debug

server:
  port: ${PORT:8001}
```

Yêu cầu:
- Never commit .env.prod (sensitive data)
- Use .env.example cho documentation
- Docker secrets cho production (Docker Swarm/K8s)
- Validate required env vars on startup
- Support environment-specific configs (dev, staging, prod)
```

---

## IX. INTEGRATION TESTING & DEPLOYMENT

### Prompt 20: API Integration Testing (Postman / REST Client)
```
Hãy tạo collection Postman để test toàn bộ API:

File: parroto-api.postman_collection.json

Các test cases:

1. Auth Flow:
   - POST /auth/register (create new account)
   - POST /auth/login (get JWT token)
   - GET /auth/profile (verify token works)
   - POST /auth/logout (blacklist token)

2. Content Flow:
   - GET /content/lessons (list lessons)
   - GET /content/lessons/{id} (get lesson detail)
   - GET /content/lessons/{id}/audio (stream audio)

3. Dictation Practice:
   - POST /practice/dictation/attempt (submit dictation)
   - GET /practice/stats (get statistics)
   - GET /practice/streak (get streak count)

4. Shadowing Practice:
   - POST /practice/shadowing/request-upload-url (get S3 URL)
   - [Upload audio to S3]
   - POST /practice/shadowing/attempt (submit recording)

5. Vocabulary Flow:
   - GET /vocabs/decks (list vocab decks)
   - GET /vocabs/decks/{id}/cards (get cards from deck)
   - POST /vocabs/user/add-from-lesson (save word)
   - GET /vocabs/user/review-queue (get words to review)
   - POST /vocabs/user/review (submit review)

6. Subscription Flow:
   - GET /subscriptions/plans (list plans)
   - POST /subscriptions/create-payment (initiate payment)
   - POST /subscriptions/activate-code (use promo code)
   - GET /subscriptions/user (check subscription status)

Environment variables (để reuse tokens, IDs):
```json
{
  "jwt_token": "{{JWT_TOKEN}}",
  "user_id": "{{USER_ID}}",
  "lesson_id": "{{LESSON_ID}}",
  "card_id": "{{CARD_ID}}"
}
```

Yêu cầu:
- Test happy path + error cases (401, 403, 404, 409, 422)
- Pre-request scripts để generate sample data
- Tests để verify response structure & status codes
- Load testing: Postman Runner hoặc k6
```

### Prompt 21: Deployment Guide (Docker Swarm or K8s)
```
Hãy tạo guide để deploy Parroto lên production:

Option 1: Docker Swarm (simpler)

1. Chuẩn bị:
   - Cloud VM (AWS EC2, GCP Compute, DigitalOcean)
   - Docker CE cài sẵn
   - PostgreSQL RDS (managed database)
   - Redis Cloud (managed cache)
   - S3 bucket hoặc Azure Storage
   - VNPay merchant account

2. Deploy steps:
```bash
# SSH vào server
ssh root@your-server-ip

# Initialize Docker Swarm
docker swarm init

# Clone repo
git clone https://github.com/your-org/parroto-backend.git
cd parroto-backend

# Create secrets (production credentials)
echo $VNPAY_SECRET | docker secret create vnpay_secret -
echo $JWT_SECRET | docker secret create jwt_secret -
...

# Deploy stack
docker stack deploy -c docker-compose.prod.yml parroto

# View status
docker service ls
docker service logs parroto_api-gateway

# Update service (after code change)
docker service update --force parroto_auth-service
```

Option 2: Kubernetes (more scalable)

Deployments:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api-gateway
        image: your-registry/parroto-api-gateway:latest
        ports:
        - containerPort: 8000
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jwt-secret
              key: secret
        livenessProbe:
          httpGet:
            path: /health
            port: 8000
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 512Mi
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway-service
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 8000
```

Monitoring & Logging:
- Prometheus (metrics: request count, latency, errors)
- ELK stack (logs: cloudwatch, datadog, or ELK)
- Alerts: PagerDuty, Slack notifications

Health checks:
```
GET /health → { "status": "UP", "services": {...} }
GET /health/deep → check DB, Redis, external APIs
```

CI/CD Pipeline (GitHub Actions):
```yaml
name: Deploy Parroto

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Build Docker images
      run: |
        docker build -t parroto-auth-service:latest ./services/auth-service
        docker build -t parroto-api-gateway:latest ./api-gateway
        # ... build other services
    
    - name: Push to registry
      run: |
        docker push your-registry/parroto-auth-service:latest
        # ...
    
    - name: Deploy to production
      env:
        DEPLOY_TOKEN: ${{ secrets.DEPLOY_TOKEN }}
      run: |
        # Deploy script (ssh + docker deploy or kubectl apply)
```

Yêu cầu:
- Auto-scaling policies (based on CPU/memory)
- Database backups (daily snapshots)
- Zero-downtime deployment (rolling updates)
- CDN setup cho static assets & audio files
- SSL/TLS certificates (Let's Encrypt)
```

---

## X. PERFORMANCE & OPTIMIZATION

### Prompt 22: Caching Strategy
```
Hãy implement caching tối ưu cho Parroto:

Redis Caching:
1. Lessons (low change frequency):
   - Key: "lesson:{id}" → TTL 24 hours
   - Key: "lessons:list:{level}:{topic}" → TTL 24 hours
   - Invalidate: khi lesson update

2. Vocabulary decks (static):
   - Key: "vocab:deck:{id}" → TTL 7 days
   - Key: "vocab:decks:list" → TTL 7 days

3. User session:
   - Key: "user:profile:{userId}" → TTL 1 hour
   - Key: "user:subscription:{userId}" → TTL 30 minutes
   - Invalidate: khi user update profile/subscription

4. SRS review queue:
   - Key: "user:review:queue:{userId}" → TTL 1 hour
   - Calculate on-demand, cache result

5. Statistics:
   - Key: "user:stats:{userId}" → TTL 1 hour
   - Aggregated from PRACTICE_ATTEMPT table

Implementation (Java):
```java
@Service
public class CacheService {
  @Autowired
  private RedisTemplate<String, Object> redisTemplate;
  
  public void set(String key, Object value, long ttlMinutes) {
    redisTemplate.opsForValue()
      .set(key, value, Duration.ofMinutes(ttlMinutes));
  }
  
  public Object get(String key) {
    return redisTemplate.opsForValue().get(key);
  }
  
  public void invalidate(String keyPattern) {
    Set<String> keys = redisTemplate.keys(keyPattern);
    redisTemplate.delete(keys);
  }
}
```

Yêu cầu:
- Cache-aside pattern (check cache, if miss → fetch from DB & cache)
- Avoid cache stampede (locking on cache miss)
- Set sensible TTLs (reduce stale data)
- Monitor cache hit/miss ratio (optimize)
```

### Prompt 23: Database Query Optimization
```
Hãy tối ưu database queries:

1. Use indexes properly:
   - Lessons: index on (cefrLevel, topicTag, createdAt)
   - Practice_attempt: index on (userId, createdAt)
   - User_vocab: index on (userId, nextReviewAt)

2. Batch operations (n+1 query problem):
   - Lesson + Sentences: use JOIN, not 1+N queries
   - User + Stats: aggregate in 1 query

3. Pagination:
   - Never fetch all rows, use LIMIT + OFFSET
   - For large tables, use cursor-based pagination (keyset pagination)

4. Query examples:

   -- Get lesson with all sentences (1 query, not 1+N)
   SELECT l.id, l.title, s.id, s.text FROM lesson l
   JOIN sentence s ON l.id = s.lesson_id
   WHERE l.id = ?;

   -- Get user stats (aggregate in 1 query)
   SELECT 
     COUNT(*) as totalAttempts,
     AVG(accuracy) as avgAccuracy,
     COUNT(DISTINCT DATE(created_at)) as daysActive
   FROM practice_attempt WHERE user_id = ?;

   -- SRS review queue (index on nextReviewAt)
   SELECT uv.* FROM user_vocab uv
   WHERE uv.user_id = ? AND uv.next_review_at <= NOW()
   ORDER BY uv.next_review_at ASC
   LIMIT 20;

5. Query analysis:
   - Use EXPLAIN ANALYZE to optimize slow queries
   - Monitor slow query log (log queries > 1 second)
   - Use PostgreSQL extensions: pg_stat_statements

Yêu cầu:
- Write efficient queries from day 1
- Monitor query performance in production
- Use connection pooling (HikariCP for Java)
```

---

## XI. MONITORING & LOGGING

### Prompt 24: Centralized Logging & Monitoring
```
Hãy setup logging & monitoring cho Parroto:

Logging (ELK Stack hoặc CloudWatch):
```

Application logs (Logback):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property name="LOG_FILE" value="logs/parroto.log"/>
  
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}</file>
    <encoder>
      <pattern>
        %d{ISO8601} [%thread] %-5level %logger{36} - %msg%n
      </pattern>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>logs/parroto.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
  </appender>

  <root level="INFO">
    <appender-ref ref="FILE"/>
  </root>

  <logger name="com.parroto" level="DEBUG"/>
</configuration>
```

Metrics (Micrometer + Prometheus):
```java
@Service
public class PracticeService {
  private final MeterRegistry meterRegistry;
  
  public void recordAttempt(PracticeAttempt attempt) {
    // Count attempts
    Counter.builder("practice.attempts")
      .tag("mode", attempt.getMode())
      .register(meterRegistry)
      .increment();
    
    // Record accuracy
    Timer.builder("practice.accuracy")
      .record(attempt.getAccuracy(), TimeUnit.MILLISECONDS);
    
    // Gauge: current pending reviews
    AtomicInteger pendingReviews = new AtomicInteger();
    Gauge.builder("vocabulary.pending_reviews", pendingReviews, AtomicInteger::get)
      .register(meterRegistry);
  }
}
```

Prometheus endpoints:
```
GET /actuator/metrics → list all metrics
GET /actuator/prometheus → Prometheus format
```

Alerts (PagerDuty / Slack):
```yaml
# Example alert: high error rate
alert: HighErrorRate
  expr: (rate(http_requests_total{status=~"5.."}[5m]) > 0.05)
  for: 5m
  annotations:
    summary: "High error rate detected"
    description: "Error rate > 5% for 5 minutes"
```

Yêu cầu:
- Log every API request (request ID, method, path, status, duration)
- Log database queries (with timing)
- Log external API calls (Azure, VNPay)
- Alert on: high error rate, slow responses, service down
- Dashboard: request rates, latency, errors by service
```

---

## SUMMARY: Execution Order

**Phase 1: Infrastructure & Database (Week 1)**
- Prompts 1-2: Project setup, database schema
- Prompt 18: Database initialization script
- Prompt 19: Environment configuration

**Phase 2: Core Services (Week 2-3)**
- Prompts 3-4: Auth Service
- Prompts 5-6: Content Service
- Prompts 7-9: Practice Service

**Phase 3: Vocabulary & Subscription (Week 4)**
- Prompts 10-12: Vocabulary Service
- Prompts 13-14: Subscription Service

**Phase 4: Integration & Deployment (Week 5)**
- Prompt 15: API Gateway
- Prompts 16-17: Docker & Docker Compose
- Prompts 20-24: Testing, deployment, monitoring

---

## Next Steps

1. **Choose tech stack confirmation**: Java Spring Boot + Node.js (Gateway) or Go? PostgreSQL + Redis ✓
2. **External APIs setup**: AWS S3 bucket, Azure Speech API credentials, VNPay sandbox
3. **Development environment**: Docker installed, VS Code/IntelliJ setup
4. **Project repository**: GitHub with CI/CD pipeline (GitHub Actions)
5. **Team communication**: Slack/Discord for dev updates

Good luck! 🚀
