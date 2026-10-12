-- =============================================
-- PARROTO BACKEND - DATABASE INITIALIZATION
-- =============================================
-- This script runs automatically when PostgreSQL starts in Docker
-- It creates separate databases for each microservice (Database per Service pattern)

-- Create database users with specific permissions
CREATE ROLE auth_user WITH LOGIN PASSWORD 'auth_password' CREATEDB;
CREATE ROLE content_user WITH LOGIN PASSWORD 'content_password' CREATEDB;
CREATE ROLE practice_user WITH LOGIN PASSWORD 'practice_password' CREATEDB;
CREATE ROLE vocabulary_user WITH LOGIN PASSWORD 'vocabulary_password' CREATEDB;
CREATE ROLE subscription_user WITH LOGIN PASSWORD 'subscription_password' CREATEDB;

-- Create databases for each microservice
CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE content_db OWNER content_user;
CREATE DATABASE practice_db OWNER practice_user;
CREATE DATABASE vocabulary_db OWNER vocabulary_user;
CREATE DATABASE subscription_db OWNER subscription_user;

-- ========================================
-- AUTH_DB SCHEMA
-- ========================================
\c auth_db auth_user

CREATE TABLE "user" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255),
  auth_provider VARCHAR(50) NOT NULL DEFAULT 'email', -- email|google|apple
  cefr_level VARCHAR(2) NOT NULL DEFAULT 'B1', -- A1|A2|B1|B2|C1|C2
  total_hours_learned DECIMAL DEFAULT 0,
  total_words_learned INTEGER DEFAULT 0,
  avatar_url TEXT,
  is_active BOOLEAN DEFAULT true,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_cefr_level ON "user"(cefr_level);
CREATE INDEX idx_user_created_at ON "user"(created_at DESC);
CREATE INDEX idx_user_is_active ON "user"(is_active);

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_user_updated_at
BEFORE UPDATE ON "user"
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

GRANT CONNECT ON DATABASE auth_db TO auth_user;
GRANT USAGE ON SCHEMA public TO auth_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO auth_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO auth_user;

-- ========================================
-- CONTENT_DB SCHEMA
-- ========================================
\c content_db content_user

CREATE TABLE lesson (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  source_url TEXT,
  cefr_level VARCHAR(2), -- A1|A2|B1|B2|C1|C2
  topic_tag VARCHAR(100), -- vocabulary|grammar|pronunciation|listening|etc
  audio_url TEXT NOT NULL,
  description TEXT,
  duration INTEGER, -- seconds
  is_deleted BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sentence (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lesson_id UUID NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
  sentence_order INTEGER NOT NULL, -- Position in lesson (1, 2, 3...)
  text TEXT NOT NULL, -- English sentence
  ipa VARCHAR(255), -- International Phonetic Alphabet
  translation_vi TEXT, -- Vietnamese translation
  start_ms INTEGER, -- Start time in audio (milliseconds)
  end_ms INTEGER, -- End time in audio
  audio_url TEXT, -- Separate audio file for sentence (optional)
  image_url TEXT, -- Supporting image (optional)
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indices for performance
CREATE INDEX idx_lesson_cefr_level ON lesson(cefr_level);
CREATE INDEX idx_lesson_topic_tag ON lesson(topic_tag);
CREATE INDEX idx_lesson_created_at ON lesson(created_at DESC);
CREATE INDEX idx_sentence_lesson_id ON sentence(lesson_id);
CREATE INDEX idx_sentence_order ON sentence(lesson_id, sentence_order);

-- Trigger for lesson updated_at
CREATE TRIGGER update_lesson_updated_at
BEFORE UPDATE ON lesson
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

GRANT CONNECT ON DATABASE content_db TO content_user;
GRANT USAGE ON SCHEMA public TO content_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO content_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO content_user;

-- ========================================
-- PRACTICE_DB SCHEMA
-- ========================================
\c practice_db practice_user

CREATE TABLE practice_attempt (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL, -- References auth_db.user.id
  lesson_id UUID NOT NULL, -- References content_db.lesson.id
  mode VARCHAR(20) NOT NULL, -- dictation|shadowing
  score DECIMAL, -- 0-100 or higher
  accuracy DECIMAL, -- 0-100 percentage
  word_scores JSONB, -- Array of {word, isCorrect, position, ...}
  audio_recording_url TEXT, -- For shadowing mode (uploaded to S3)
  duration DECIMAL, -- Session duration in seconds
  difficulty VARCHAR(20), -- Easy|Normal|Hard (for dictation)
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indices for fast queries
CREATE INDEX idx_practice_user_id ON practice_attempt(user_id);
CREATE INDEX idx_practice_lesson_id ON practice_attempt(lesson_id);
CREATE INDEX idx_practice_user_created ON practice_attempt(user_id, created_at DESC);
CREATE INDEX idx_practice_created_at ON practice_attempt(created_at DESC);
CREATE INDEX idx_practice_mode ON practice_attempt(mode);

GRANT CONNECT ON DATABASE practice_db TO practice_user;
GRANT USAGE ON SCHEMA public TO practice_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO practice_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO practice_user;

-- ========================================
-- VOCABULARY_DB SCHEMA
-- ========================================
\c vocabulary_db vocabulary_user

CREATE TABLE vocab_deck (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  level VARCHAR(2), -- A1-C2 (CEFR level)
  is_pro_only BOOLEAN DEFAULT false,
  card_count INTEGER DEFAULT 0, -- Denormalized for performance
  created_by UUID, -- Admin user ID
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vocab_card (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deck_id UUID NOT NULL REFERENCES vocab_deck(id) ON DELETE CASCADE,
  word VARCHAR(255) NOT NULL,
  meaning TEXT NOT NULL, -- Vietnamese meaning
  example TEXT, -- English example sentence
  example_vi TEXT, -- Vietnamese example translation
  ipa VARCHAR(255), -- Pronunciation (/həˈloʊ/)
  audio_url TEXT, -- Audio pronunciation file URL
  image_url TEXT, -- Supporting image URL
  part_of_speech VARCHAR(50), -- noun, verb, adjective, etc.
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- User's personal vocabulary learning tracker (SRS)
CREATE TABLE user_vocab (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL, -- References auth_db.user.id
  card_id UUID NOT NULL REFERENCES vocab_card(id) ON DELETE CASCADE,
  
  -- SM-2 Spaced Repetition Algorithm fields
  next_review_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- When to review next
  interval_days INTEGER DEFAULT 1, -- Days until next review
  ease_factor DECIMAL DEFAULT 2.5, -- Difficulty factor (1.3-2.5)
  repetitions INTEGER DEFAULT 0, -- Number of times reviewed
  quality INTEGER, -- Last review quality (0-5 SM-2 scale)
  last_review_at TIMESTAMPTZ, -- Last review timestamp
  
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indices for SRS algorithm
CREATE INDEX idx_vocab_deck_level ON vocab_deck(level);
CREATE INDEX idx_vocab_card_deck ON vocab_card(deck_id);
CREATE INDEX idx_vocab_card_word ON vocab_card(word);
CREATE INDEX idx_user_vocab_user ON user_vocab(user_id);
CREATE INDEX idx_user_vocab_next_review ON user_vocab(user_id, next_review_at) WHERE next_review_at <= CURRENT_TIMESTAMP;
CREATE INDEX idx_user_vocab_created ON user_vocab(user_id, created_at DESC);

-- Trigger for deck updated_at
CREATE TRIGGER update_vocab_deck_updated_at
BEFORE UPDATE ON vocab_deck
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Trigger for user_vocab updated_at
CREATE TRIGGER update_user_vocab_updated_at
BEFORE UPDATE ON user_vocab
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

GRANT CONNECT ON DATABASE vocabulary_db TO vocabulary_user;
GRANT USAGE ON SCHEMA public TO vocabulary_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO vocabulary_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO vocabulary_user;

-- ========================================
-- SUBSCRIPTION_DB SCHEMA
-- ========================================
\c subscription_db subscription_user

CREATE TABLE plan (
  id VARCHAR(50) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  duration_days INTEGER NOT NULL,
  price BIGINT NOT NULL, -- VND (Vietnamese Dong)
  features JSONB, -- Array of feature strings
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscription (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL, -- References auth_db.user.id
  plan_id VARCHAR(50) NOT NULL REFERENCES plan(id),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) DEFAULT 'active', -- active|expired|cancelled
  payment_method VARCHAR(50), -- vnpay|momo|activation_code
  transaction_id VARCHAR(255), -- VNPay transaction ID
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_transaction (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL, -- References auth_db.user.id
  plan_id VARCHAR(50), -- References plan.id (optional, for activation codes)
  amount BIGINT NOT NULL, -- VND
  method VARCHAR(50), -- vnpay|momo|gift_code
  status VARCHAR(20) DEFAULT 'pending', -- pending|success|failed
  transaction_id VARCHAR(255) UNIQUE, -- VNPay/MoMo transaction reference
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activation_code (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(50) UNIQUE NOT NULL, -- GiveCode format: PROMO-XXXXX-XXXXX
  plan_id VARCHAR(50) NOT NULL REFERENCES plan(id),
  used_by_user_id UUID, -- References auth_db.user.id (NULL if unused)
  used_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX idx_plan_active ON plan(created_at DESC);
CREATE INDEX idx_subscription_user ON subscription(user_id);
CREATE INDEX idx_subscription_end_at ON subscription(end_at) WHERE status = 'active';
CREATE INDEX idx_subscription_status ON subscription(status);
CREATE INDEX idx_payment_user ON payment_transaction(user_id);
CREATE INDEX idx_payment_status ON payment_transaction(status);
CREATE INDEX idx_activation_code ON activation_code(code);
CREATE INDEX idx_activation_code_expires ON activation_code(expires_at);

-- Triggers
CREATE TRIGGER update_subscription_updated_at
BEFORE UPDATE ON subscription
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Insert sample plans
INSERT INTO plan (id, name, duration_days, price, features) VALUES
  ('plan-1month', '1 tháng Premium', 30, 99000, '["Không quảng cáo", "Tất cả bộ từ vựng Pro", "Sổ tay không giới hạn"]'::jsonb),
  ('plan-3month', '3 tháng Premium', 90, 199000, '["Không quảng cáo", "Tất cả bộ từ vựng Pro", "Sổ tay không giới hạn", "Ưu tiên hỗ trợ"]'::jsonb),
  ('plan-1year', '1 năm Premium', 365, 299000, '["Không quảng cáo", "Tất cả bộ từ vựng Pro", "Sổ tay không giới hạn", "Ưu tiên hỗ trợ", "Ghi chú không giới hạn"]'::jsonb),
  ('plan-lifetime', 'Premium trọn đời', 36500, 599000, '["Không quảng cáo", "Tất cả bộ từ vựng Pro", "Sổ tay không giới hạn", "Ưu tiên hỗ trợ", "Ghi chú không giới hạn", "Truy cập tất cả tính năng mới"]'::jsonb);

GRANT CONNECT ON DATABASE subscription_db TO subscription_user;
GRANT USAGE ON SCHEMA public TO subscription_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO subscription_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO subscription_user;

-- ========================================
-- FINAL SETUP
-- ========================================
-- Connect back to main database
\c parroto

-- Grant all users access to their respective databases
GRANT CONNECT ON DATABASE auth_db TO auth_user;
GRANT CONNECT ON DATABASE content_db TO content_user;
GRANT CONNECT ON DATABASE practice_db TO practice_user;
GRANT CONNECT ON DATABASE vocabulary_db TO vocabulary_user;
GRANT CONNECT ON DATABASE subscription_db TO subscription_user;

-- Status message
SELECT 'Database initialization completed successfully!' as status;
