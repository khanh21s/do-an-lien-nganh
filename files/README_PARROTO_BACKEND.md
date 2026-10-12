# 🚀 Parroto Backend - Getting Started Guide

## 📋 Tổng Quan

Parroto là nền tảng học tiếng Anh với 3 tính năng chính:
- **Dictation** (Nghe chép): Nghe và gõ lại câu
- **Shadowing** (Nói nhại): Ghi âm nhại câu, AI chấm phát âm
- **Vocabulary SRS** (Ôn tập thông minh): Ôn từ vựng theo thuật toán SM-2

### Kiến Trúc

```
Frontend (React/Next.js, Port 3000)
    ↓
API Gateway (Node.js Express, Port 8000)
    ├→ Auth Service (Java Spring Boot, Port 8001)
    ├→ Content Service (Java Spring Boot, Port 8002)
    ├→ Practice Service (Java Spring Boot, Port 8003)
    ├→ Vocabulary Service (Java Spring Boot, Port 8004)
    └→ Subscription Service (Java Spring Boot, Port 8005)
    
Database: PostgreSQL 16 (Port 5432)
Cache: Redis 7 (Port 6379)
Storage: AWS S3 (audio/video files)
```

---

## 📦 Yêu Cầu Hệ Thống

### Bắt Buộc
- **Docker** >= 20.10 ([Download](https://www.docker.com/products/docker-desktop))
- **Docker Compose** >= 2.0
- **Git** để clone repository

### Tùy Chọn (cho phát triển không dùng Docker)
- **Java** 25+ (Eclipse Temurin)
- **Node.js** 20+
- **PostgreSQL** 16
- **Redis** 7
- **Maven** 3.9+
- **npm** hoặc **yarn**

---

## 🚀 Quick Start (5 phút)

### 1. Clone Repository
```bash
git clone https://github.com/your-org/parroto-backend.git
cd parroto-backend
```

### 2. Setup Environment Variables
```bash
# Copy template
cp .env.example .env

# Edit .env (mở file và thay đổi các giá trị)
nano .env  # hoặc editor yêu thích của bạn
```

Các biến chính cần set:
- `DB_PASSWORD`: Database password
- `JWT_SECRET`: JWT secret key (tối thiểu 32 ký tự)
- `AWS_ACCESS_KEY_ID`: AWS S3 credentials
- `AWS_SECRET_ACCESS_KEY`: AWS S3 credentials
- `AZURE_SPEECH_KEY`: Azure Speech API key (optional, có thể skip)
- `VNPAY_API_KEY`: VNPay key (optional, có thể skip)

### 3. Start All Services
```bash
# Start all services in background
docker-compose up -d

# Watch the logs
docker-compose logs -f api-gateway
```

### 4. Verify Services are Running
```bash
# Check all services
docker-compose ps

# Test API Gateway
curl http://localhost:8000/health

# Expected response:
# {"status":"OK","timestamp":"2024-01-15T10:30:00Z"}
```

### 5. Test API
```bash
# Register a new user
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "cefrLevel": "B1"
  }'

# Response:
# {
#   "userId": "uuid...",
#   "email": "test@example.com",
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "expiresIn": 3600
# }
```

**Chúc mừng! Backend đang chạy.** 🎉

---

## 📁 Cấu Trúc Dự Án

```
parroto-backend/
├── docker-compose.yml              # Compose file for all services
├── .env.example                    # Environment variables template
├── init-db.sql                     # Database initialization script
├── README_PARROTO_BACKEND.md       # This file
├── parroto-backend-prompts.md      # Detailed implementation prompts
│
├── api-gateway/                    # API Gateway (Node.js)
│   ├── Dockerfile
│   ├── package.json
│   ├── server.js
│   └── middleware/
│       ├── auth.js                 # JWT validation
│       ├── errorHandler.js
│       └── logger.js
│
├── services/
│   ├── auth-service/               # Authentication & User Management (Java)
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   ├── src/
│   │   │   └── main/java/com/parroto/auth/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       └── config/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   │
│   ├── content-service/            # Lesson & Audio Management
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   ├── src/...
│   │   └── resources/...
│   │
│   ├── practice-service/           # Dictation & Shadowing Practice
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   ├── src/...
│   │   └── resources/...
│   │
│   ├── vocabulary-service/         # Vocabulary & SRS Management
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   ├── src/...
│   │   └── resources/...
│   │
│   └── subscription-service/       # Billing & Subscriptions
│       ├── Dockerfile
│       ├── pom.xml
│       ├── src/...
│       └── resources/...
│
├── scripts/
│   ├── test-api.sh                 # API testing script
│   ├── load-test.sh                # Load testing with k6
│   └── deploy.sh                   # Deployment script
│
└── docs/
    ├── API.md                      # API documentation
    ├── DATABASE_SCHEMA.md          # Database design
    ├── DEPLOYMENT.md               # Production deployment
    └── TROUBLESHOOTING.md          # Common issues & solutions
```

---

## 🐳 Docker Commands

### View Logs
```bash
# All services
docker-compose logs -f

# Single service
docker-compose logs -f auth-service

# Last 100 lines
docker-compose logs --tail=100 api-gateway

# Real-time with timestamps
docker-compose logs -f --timestamps api-gateway
```

### Access Services
```bash
# PostgreSQL CLI
docker-compose exec postgres psql -U parroto -d parroto_db

# Redis CLI
docker-compose exec redis redis-cli

# Auth Service Shell
docker-compose exec auth-service /bin/sh
```

### Management
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (⚠️ deletes data)
docker-compose down -v

# Rebuild services
docker-compose up -d --build

# Restart single service
docker-compose restart auth-service

# View running processes
docker-compose ps

# Resource usage
docker stats parroto-api-gateway
```

### Database Backup
```bash
# Backup PostgreSQL
docker-compose exec postgres pg_dump -U parroto parroto_db > backup.sql

# Restore
docker-compose exec -T postgres psql -U parroto parroto_db < backup.sql
```

---

## 🧪 Testing

### 1. Unit Tests
```bash
# Auth Service
cd services/auth-service
mvn clean test

# All services
cd ../../
mvn clean test -DskipIntegrationTests
```

### 2. Integration Tests
```bash
# Run with Docker services running
mvn clean test -DincludeIntegrationTests
```

### 3. API Testing with Postman
```bash
# Import collection
1. Open Postman
2. File → Import → parroto-api.postman_collection.json
3. Set environment variables (jwt_token, user_id, etc.)
4. Run tests
```

### 4. Load Testing with k6
```bash
# Install k6: https://k6.io/docs/getting-started/installation/

# Simple load test
k6 run scripts/load-test.js

# Advanced with options
k6 run --vus 100 --duration 30s scripts/load-test.js
```

### 5. Manual API Testing
```bash
# Register
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123",
    "cefrLevel": "B1"
  }'

# Login
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123"
  }'

# Get Profile (replace TOKEN with actual token)
curl -X GET http://localhost:8000/auth/profile \
  -H "Authorization: Bearer TOKEN"

# List Lessons
curl http://localhost:8000/content/lessons?limit=10

# Submit Dictation
curl -X POST http://localhost:8000/practice/dictation/attempt \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lessonId": "lesson-uuid",
    "sentenceId": "sentence-uuid",
    "userInput": "hello world",
    "difficulty": "Normal"
  }'
```

---

## 🔍 Troubleshooting

### Services not starting
```bash
# Check logs
docker-compose logs

# Common issues:
# 1. Port already in use
lsof -i :8000  # Check port 8000
kill -9 <PID>  # Kill process

# 2. PostgreSQL not ready
docker-compose down
docker volume rm parroto-backend_postgres_data  # Remove old data
docker-compose up -d

# 3. Out of memory
docker run --memory=4g  # Allocate more memory
```

### Database connection error
```bash
# Check PostgreSQL is running
docker-compose ps

# Connect directly
psql -h localhost -U parroto -d parroto_db

# Check connection string
echo $SPRING_DATASOURCE_URL
```

### Redis connection error
```bash
# Check Redis
docker-compose exec redis redis-cli ping

# Clear Redis cache
docker-compose exec redis redis-cli FLUSHALL
```

### JWT token issues
```bash
# Generate new JWT secret
openssl rand -base64 32

# Update .env and restart services
docker-compose restart auth-service api-gateway
```

### Memory/CPU Issues
```bash
# Check Docker Desktop settings:
# - Preferences → Resources → Memory: increase to 4-6GB
# - CPU: increase to 4-6 cores

# Or use Docker CLI limits:
docker run -m 2g --cpus 2 ...
```

---

## 📝 Development Workflow

### 1. Make Code Changes
```bash
# Edit service code
vim services/auth-service/src/main/java/...
```

### 2. Rebuild Service
```bash
# Rebuild and restart
docker-compose up -d --build auth-service

# View logs
docker-compose logs -f auth-service
```

### 3. Run Tests
```bash
# Test specific service
cd services/auth-service
mvn clean test
```

### 4. Check Database
```bash
docker-compose exec postgres psql -U parroto parroto_db

# In psql:
\dt                    # List tables
SELECT * FROM "user";  # Query
\q                     # Exit
```

### 5. View Metrics
```bash
# Access Prometheus
curl http://localhost:9090/metrics

# Access service metrics
curl http://localhost:8001/actuator/metrics
curl http://localhost:8001/actuator/prometheus
```

---

## 🚀 Deployment

### Development (Local)
```bash
# Already running with docker-compose up -d
```

### Staging (Cloud VM)
```bash
# See DEPLOYMENT.md for detailed guide
bash scripts/deploy.sh staging
```

### Production
```bash
# Pre-deployment checklist
# [ ] Change .env ENVIRONMENT=production
# [ ] Use strong JWT_SECRET
# [ ] Configure real AWS S3
# [ ] Setup HTTPS/SSL
# [ ] Enable monitoring

bash scripts/deploy.sh production
```

---

## 📚 File Descriptions

| File | Purpose |
|------|---------|
| `parroto-backend-prompts.md` | 24 detailed prompts for implementing each component |
| `docker-compose.yml` | Define all services, volumes, networks |
| `.env.example` | Template for environment variables |
| `init-db.sql` | SQL schema initialization |
| `README_PARROTO_BACKEND.md` | This file - Getting started guide |
| `scripts/test-api.sh` | Automated API testing |
| `docs/API.md` | Complete API documentation |
| `docs/DATABASE_SCHEMA.md` | Database design & relationships |
| `docs/DEPLOYMENT.md` | Production deployment guide |

---

## 🔑 Key Concepts

### Microservices Architecture
- **Database per Service**: Each service has its own PostgreSQL database
- **API Gateway**: Single entry point for all client requests
- **Service-to-Service Communication**: Internal HTTP calls (sync) or message queue (async)

### Authentication
- **JWT Tokens**: Stateless authentication with JWT
- **Token Refresh**: Refresh tokens for long-lived sessions
- **Token Blacklist**: Redis cache for logout

### Caching
- **Redis Cache**: Cache lessons, vocabulary decks, user stats
- **TTL Strategy**: Different TTLs for different data types
- **Cache Invalidation**: Update cache when data changes

### Database
- **PostgreSQL**: Relational data with ACID guarantees
- **Indexes**: Optimized queries for search, sort, filtering
- **Triggers**: Auto-update timestamps

### Vocabulary SRS
- **SM-2 Algorithm**: Spaced repetition with adaptive intervals
- **Ease Factor**: Track difficulty of each word
- **Next Review**: Schedule reviews based on performance

---

## 📞 Support & Resources

- **API Documentation**: See `docs/API.md`
- **Database Schema**: See `docs/DATABASE_SCHEMA.md`
- **Troubleshooting**: See `docs/TROUBLESHOOTING.md`
- **Implementation Guide**: See `parroto-backend-prompts.md`

---

## ✅ Checklist - Báo Lên Phát Triển

### Phase 1: Setup (Week 1)
- [ ] Clone repository
- [ ] Setup environment variables
- [ ] Start Docker services
- [ ] Verify all services running
- [ ] Test basic API endpoints

### Phase 2: Core Services (Week 2-3)
- [ ] Implement Auth Service
- [ ] Implement Content Service
- [ ] Implement Practice Service
- [ ] Write unit tests

### Phase 3: Vocabulary & Billing (Week 4)
- [ ] Implement Vocabulary Service
- [ ] Implement Subscription Service
- [ ] Integration tests

### Phase 4: Deployment (Week 5)
- [ ] Setup CI/CD pipeline
- [ ] Deploy to staging
- [ ] Performance testing
- [ ] Security audit
- [ ] Deploy to production

---

## 📄 License

Licensed under MIT License - see LICENSE file

---

## 🤝 Contributing

1. Create feature branch: `git checkout -b feature/your-feature`
2. Commit changes: `git commit -am 'Add feature'`
3. Push to branch: `git push origin feature/your-feature`
4. Open Pull Request

---

**Happy coding! 🚀**

Nếu có câu hỏi, hãy tạo GitHub Issue hoặc liên hệ team development.

