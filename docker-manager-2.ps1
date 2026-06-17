# docker-manager.ps1 - Docker Container Manager
# Usage: .\\docker-manager.ps1 [start|stop|restart|rebuild|status|initdb|clean|help]

param(
    [Parameter(Position=0)]
    [ValidateSet("start", "stop", "restart", "rebuild", "status", "initdb", "clean", "help")]
    [string]$Action = "help"
)

# ========== Color Functions ==========
function Write-Step {
    param([string]$Message, [string]$Color = "Yellow")
    Write-Host "`n[$($Action.ToUpper())] $Message" -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Host "      [OK] $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "      [FAIL] $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "      [i] $Message" -ForegroundColor Gray
}

function Write-Warning {
    param([string]$Message)
    Write-Host "      [!] $Message" -ForegroundColor Yellow
}

# ========== Help ==========
function Show-Help {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Docker Container Manager" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\\docker-manager.ps1 [command]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Cyan
    Write-Host "  start      - Start all containers (MySQL, Redis, RabbitMQ, App)" -ForegroundColor Green
    Write-Host "  stop       - Stop all containers" -ForegroundColor Green
    Write-Host "  restart    - Restart all containers" -ForegroundColor Green
    Write-Host "  rebuild    - Rebuild and redeploy app (after code change)" -ForegroundColor Green
    Write-Host "  status     - Show container status" -ForegroundColor Green
    Write-Host "  initdb     - Initialize database (create tables and seed data)" -ForegroundColor Green
    Write-Host "  clean      - Remove all containers (keep mysql data volume)" -ForegroundColor Green
    Write-Host "  help       - Show this help" -ForegroundColor Green
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Cyan
    Write-Host "  .\\docker-manager.ps1 start" -ForegroundColor Gray
    Write-Host "  .\\docker-manager.ps1 rebuild" -ForegroundColor Gray
    Write-Host "  .\\docker-manager.ps1 stop" -ForegroundColor Gray
}

# ========== Helper Functions ==========
function Container-Exists {
    param([string]$Name)
    return [bool](docker ps -a --filter name=$Name -q)
}

function Container-IsRunning {
    param([string]$Name)
    return [bool](docker ps --filter name=$Name --filter status=running -q)
}

function Wait-ForMySQL {
    Write-Info "Waiting for MySQL to be ready..."
    for ($i = 1; $i -le 60; $i++) {
        $result = docker exec mysql-demo mysqladmin ping -h localhost -u root -p123456 2>$null
        if ($result -match "mysqld is alive") {
            Write-Success "MySQL is ready"
            return $true
        }
        if ($i % 10 -eq 0) { Write-Info "Still waiting for MySQL... ($i/60)" }
        Start-Sleep -Seconds 1
    }
    Write-Error "MySQL failed to start"
    docker logs mysql-demo --tail 20
    return $false
}

# ========== SQL Schema ==========
function Get-SchemaSQL {
    return @"
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(40) NOT NULL UNIQUE,
    `email` VARCHAR(60) NOT NULL UNIQUE,
    `password` VARCHAR(100) NOT NULL,
    `bio` TEXT,
    `image` VARCHAR(200),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`)
);

CREATE TABLE IF NOT EXISTS `article` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `slug` VARCHAR(200) NOT NULL UNIQUE,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT NOT NULL,
    `body` TEXT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`author_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    INDEX `idx_slug` (`slug`)
);

CREATE TABLE IF NOT EXISTS `comment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `body` TEXT NOT NULL,
    `article_id` BIGINT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`author_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    INDEX `idx_name` (`name`)
);

CREATE TABLE IF NOT EXISTS `user_favorites` (
    `user_id` BIGINT NOT NULL,
    `article_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `article_id`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `user_follows` (
    `follower_id` BIGINT NOT NULL,
    `followee_id` BIGINT NOT NULL,
    PRIMARY KEY (`follower_id`, `followee_id`),
    FOREIGN KEY (`follower_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`followee_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `article_tags` (
    `article_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    PRIMARY KEY (`article_id`, `tag_id`),
    FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`tag_id`) REFERENCES `tag`(`id`) ON DELETE CASCADE
);
"@
}

function Init-Database {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Initialize Database" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    if (-not (Container-IsRunning "mysql-demo")) {
        Write-Error "MySQL container not running. Please run: .\\docker-manager.ps1 start"
        return
    }

    Write-Step "Creating database and tables" -Color Yellow

    # Get schema and pipe to mysql
    $schema = Get-SchemaSQL
    $schema | docker exec -i mysql-demo mysql -u root -p123456 realworld 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Database schema created"
    } else {
        Write-Error "Failed to create schema"
        return
    }

    Write-Step "Inserting seed data" -Color Yellow

    $seedData = @"
INSERT IGNORE INTO `user` (`username`, `email`, `password`, `bio`, `image`) VALUES
('johnjacob1', 'john@example1.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'I love coding!', NULL),
('alice', 'alice@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Frontend developer', NULL),
('bob', 'bob@example.com', '$2a$10$NkMZQjK7hLpX9YrV9qZvWuRjLkMpQrStUvWxYzAbCdEfGhIjKlMn', 'Backend developer', NULL);

INSERT IGNORE INTO `article` (`slug`, `title`, `description`, `body`, `author_id`) VALUES
('how-to-train-your-dragon', 'How to train your dragon', 'Ever wonder how?', 'You have to believe', 1),
('my-first-post', 'My First Post', 'Welcome to my blog', 'This is my first article', 1),
('java-tutorial', 'Java Tutorial', 'Learn Java step by step', 'Java is a great language', 1);

INSERT IGNORE INTO `tag` (`name`) VALUES
('dragons'), ('training'), ('java'), ('programming'), ('tutorial'), ('spring'), ('database'), ('docker');

INSERT IGNORE INTO `article_tags` (`article_id`, `tag_id`) VALUES
(1, 1), (1, 2),
(3, 3), (3, 4), (3, 5);

INSERT IGNORE INTO `user_follows` (`follower_id`, `followee_id`) VALUES
(2, 1), (3, 1);

INSERT IGNORE INTO `user_favorites` (`user_id`, `article_id`) VALUES
(2, 1), (3, 1);
"@

    $seedData | docker exec -i mysql-demo mysql -u root -p123456 realworld 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Seed data inserted"
    } else {
        Write-Warning "Seed data may already exist or partial"
    }

    Write-Step "Verifying data" -Color Yellow
    docker exec mysql-demo mysql -u root -p123456 -e "USE realworld; SELECT COUNT(*) as total_users FROM user; SELECT COUNT(*) as total_articles FROM article; SELECT COUNT(*) as total_tags FROM tag;"

    Write-Host "`nDatabase initialization completed" -ForegroundColor Green
}

# ========== Core Actions ==========
function Start-Containers {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Start All Containers" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    # Check Docker
    Write-Step "Check Docker" -Color Yellow
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker is not running. Please start Docker Desktop first."
        return
    }
    Write-Success "Docker is running"

    # Check ports
    Write-Step "Check ports" -Color Yellow
    $portsToCheck = @(3306, 6379, 5672, 15672, 8080)
    foreach ($port in $portsToCheck) {
        $portInUse = netstat -ano | Select-String "LISTENING" | Select-String ":$port "
        if ($portInUse) {
            Write-Warning "Port $port is in use. This may cause issues."
        }
    }

    # Create network
    Write-Step "Create network" -Color Yellow
    docker network create demo-net 2>$null
    Write-Success "Network demo-net ready"

    # Remove existing containers
    Write-Step "Clean up existing containers" -Color Yellow
    @("demo-app", "mysql-demo", "redis-demo", "rabbitmq-demo") | ForEach-Object {
        if (Container-Exists $_ ) {
            docker rm -f $_ 2>$null
            Write-Info "Removed existing container: $_"
        }
    }

    # Start MySQL
    Write-Step "Start MySQL" -Color Yellow
    docker run -d --name mysql-demo --network demo-net `
        -e MYSQL_ROOT_PASSWORD=123456 `
        -e MYSQL_DATABASE=realworld `
        -p 3306:3306 `
        -v mysql-data:/var/lib/mysql `
        mysql:8.0
    Write-Success "MySQL container created"

    # Start Redis
    Write-Step "Start Redis" -Color Yellow
    docker run -d --name redis-demo --network demo-net -p 6379:6379 redis:alpine
    Write-Success "Redis container created"

    # Start RabbitMQ
    Write-Step "Start RabbitMQ" -Color Yellow
    docker run -d --name rabbitmq-demo --network demo-net `
        -p 5672:5672 -p 15672:15672 `
        -e RABBITMQ_DEFAULT_USER=guest `
        -e RABBITMQ_DEFAULT_PASS=guest `
        rabbitmq:management
    Write-Success "RabbitMQ container created"

    # Wait for MySQL
    Write-Step "Wait for MySQL ready" -Color Yellow
    $mysqlOk = Wait-ForMySQL
    if (-not $mysqlOk) {
        Write-Error "MySQL failed to start. Exiting."
        return
    }

    # Initialize database
    Write-Step "Initialize database" -Color Yellow
    Init-Database

    # Start App
    Write-Step "Start App" -Color Yellow
    $sqlUrl = 'jdbc:mysql://mysql-demo:3306/realworld?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    docker run -d --name demo-app --network demo-net `
        -e "SPRING_DATASOURCE_URL=$sqlUrl" `
        -e SPRING_DATASOURCE_USERNAME=root `
        -e SPRING_DATASOURCE_PASSWORD=123456 `
        -e SPRING_DATA_REDIS_HOST=redis-demo `
        -e SPRING_DATA_REDIS_PORT=6379 `
        -e SPRING_RABBITMQ_HOST=rabbitmq-demo `
        -e SPRING_RABBITMQ_PORT=5672 `
        -e SPRING_RABBITMQ_USERNAME=guest `
        -e SPRING_RABBITMQ_PASSWORD=guest `
        -e SPRING_AMQP_DESERIALIZATION_TRUST_ALL=true `
        -e JAVA_TOOL_OPTIONS="-Djdk.lang.Process.launchMechanism=POSIX_SPAWN" `
        -p 8080:8080 demo:1.0
    Write-Success "App container created"

    # Verify network
    Write-Step "Verify network" -Color Yellow
    $containersInNetwork = docker network inspect demo-net --format "{{range .Containers}}{{.Name}} {{end}}"
    Write-Info "Containers in demo-net: $containersInNetwork"

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  Start completed. Showing logs..." -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "RabbitMQ Management: http://localhost:15672 (guest/guest)" -ForegroundColor Green
    Write-Host "API: http://localhost:8080/api/tags" -ForegroundColor Green
    Start-Sleep -Seconds 5
    docker logs -f demo-app
}

function Stop-Containers {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Stop All Containers" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    $containers = @("demo-app", "mysql-demo", "redis-demo", "rabbitmq-demo")
    foreach ($container in $containers) {
        if (Container-IsRunning $container) {
            docker stop $container
            Write-Success "$container stopped"
        } else {
            Write-Info "$container not running"
        }
    }
    Write-Host "`nAll containers stopped" -ForegroundColor Green
}

function Restart-Containers {
    Stop-Containers
    Start-Sleep -Seconds 2
    Start-Containers
}

function Rebuild-App {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Rebuild and Redeploy App" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    Write-Step "Maven package" -Color Yellow
    mvn clean package
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed"
        exit 1
    }
    Write-Success "Maven build success"

    Write-Step "Build Docker image" -Color Yellow
    docker build -t demo:1.0 .
    Write-Success "Image built"

    Write-Step "Restart app container" -Color Yellow
    if (Container-Exists "demo-app") {
        docker stop demo-app 2>$null
        docker rm demo-app 2>$null
    }

    $sqlUrl = 'jdbc:mysql://mysql-demo:3306/realworld?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
    docker run -d --name demo-app --network demo-net `
        -e "SPRING_DATASOURCE_URL=$sqlUrl" `
        -e SPRING_DATASOURCE_USERNAME=root `
        -e SPRING_DATASOURCE_PASSWORD=123456 `
        -e SPRING_DATA_REDIS_HOST=redis-demo `
        -e SPRING_DATA_REDIS_PORT=6379 `
        -e SPRING_RABBITMQ_HOST=rabbitmq-demo `
        -e SPRING_RABBITMQ_PORT=5672 `
        -e SPRING_RABBITMQ_USERNAME=guest `
        -e SPRING_RABBITMQ_PASSWORD=guest `
        -e SPRING_AMQP_DESERIALIZATION_TRUST_ALL=true `
        -e JAVA_TOOL_OPTIONS="-Djdk.lang.Process.launchMechanism=POSIX_SPAWN" `
        -p 8080:8080 demo:1.0
    Write-Success "App restarted"

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  Showing logs..." -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Start-Sleep -Seconds 3
    docker logs -f demo-app
}

function Show-Status {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Container Status" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    Write-Host "`n[Network] demo-net" -ForegroundColor Yellow
    docker network ls --filter name=demo-net

    Write-Host "`n[Containers in demo-net]" -ForegroundColor Yellow
    $containersInNetwork = docker network inspect demo-net --format "{{range .Containers}}{{.Name}} {{end}}"
    if ($containersInNetwork) {
        Write-Host "      $containersInNetwork" -ForegroundColor Green
    } else {
        Write-Host "      (no containers)" -ForegroundColor Gray
    }

    Write-Host "`n[All Containers]" -ForegroundColor Yellow
    docker ps -a --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"
}

function Clean-Containers {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  Clean Containers" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    $containers = @("demo-app", "mysql-demo", "redis-demo", "rabbitmq-demo")
    foreach ($container in $containers) {
        if (Container-Exists $container) {
            docker stop $container 2>$null
            docker rm $container 2>$null
            Write-Success "$container removed"
        } else {
            Write-Info "$container not exists"
        }
    }
    Write-Host "`nContainers cleaned. MySQL data volume 'mysql-data' is preserved." -ForegroundColor Green
    Write-Host "To delete data volume completely, run: docker volume rm mysql-data" -ForegroundColor Yellow
}

# ========== Main ==========
switch ($Action) {
    "start"   { Start-Containers }
    "stop"    { Stop-Containers }
    "restart" { Restart-Containers }
    "rebuild" { Rebuild-App }
    "status"  { Show-Status }
    "initdb"  { Init-Database }
    "clean"   { Clean-Containers }
    default   { Show-Help }
}