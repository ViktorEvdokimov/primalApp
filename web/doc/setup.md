# Primal Web — как запустить сайт

Инструкция для запуска сайта на своём компьютере (Windows 11) и на отдельном сервере с доменом и HTTPS.

> **Состояние.** Разделы 2 и 4 проверены на Windows 11 (28.09.2026): экспедиция, вход через Mailpit,
> кампания от пролога до перехода главы, совместная игра по ссылке, резервная копия и восстановление.
> Раздел 3 ещё нужно пройти на чистом VPS с доменом и настоящей почтой (задача 7.4 плана) и исправить
> расхождения, если найдутся.

**Содержание**
1. Что понадобится
2. Запуск на своём компьютере
3. Запуск на сервере
4. Режим разработки (для программиста)
5. Переменные окружения
6. Типовые проблемы

Сайт состоит из четырёх контейнеров Docker: `postgres` (база данных), `backend` (сервер на Java),
`web` (Caddy: отдаёт страницы сайта, проксирует запросы к серверу и сам получает HTTPS-сертификат) и,
только локально, `mailpit` — «почтовый ящик-ловушка», куда приходят все письма с кодами входа.

---

## 1. Что понадобится

**Для запуска на своём компьютере:**

| Программа | Зачем | Где взять |
|-----------|-------|-----------|
| Docker Desktop | Запускает все части сайта | https://www.docker.com/products/docker-desktop — уже установлен |
| Git | Получить код | https://git-scm.com — уже установлен |

В настройках Docker Desktop (Settings → Resources) выделите не меньше 4 ГБ памяти — столько нужно для
первой сборки.

**Для сервера** — список в разделе 3.1. **Для разработки** — дополнительно JDK, Node.js и IntelliJ IDEA
(раздел 4).

---

## 2. Запуск на своём компьютере

### 2.1 Первый запуск

1. Запустите **Docker Desktop** и дождитесь зелёного статуса «Engine running».
2. Откройте PowerShell и перейдите в папку сайта:
   ```powershell
   cd D:\projekts\promptLearning\primalApp\web
   ```
3. Создайте файл настроек из примера. Значения в нём уже подходят для локального запуска:
   ```powershell
   Copy-Item .env.example .env
   ```
4. Соберите и запустите сайт:
   ```powershell
   docker compose --profile local up -d --build
   ```
   Первая сборка занимает 5–10 минут: скачиваются Java, Node.js и библиотеки. Следующие запуски — секунды.
5. Проверьте, что всё поднялось:
   ```powershell
   docker compose ps
   ```
   У `postgres`, `backend`, `web`, `mailpit` в колонке `STATUS` должно быть `Up`, у первых трёх — `(healthy)`.

### 2.2 Экспедиция и вход на сайт

Откройте http://localhost:8088. **Экспедиция** работает без входа: «Экспедиция» → подготовка → бой.
Бой идёт целиком в браузере и сохраняется в нём, поэтому вкладку можно закрыть и вернуться к бою
кнопкой «Вернуться к бою» — в этом же браузере.

Для **кампаний** нужен вход:

1. «Кампании» → введите любую почту, например `alice@example.com`: локально письма никуда не уходят.
2. Откройте http://localhost:8025 — это Mailpit. Там лежит письмо с 6-значным кодом.
3. Введите код на сайте. Браузер запомнит устройство, повторно входить не придётся.
4. «Новая кампания» → название и отряд из 2–5 охотников → сразу откроется подготовка пролога. После
   победы итог пролога принимается сам, дальше — награды главы 1 и лист кампании.

Чтобы проверить «второго игрока», откройте сайт в окне инкогнито и войдите с другой почтой. Или
поделитесь кампанией и откройте ссылку в инкогнито — так вы войдёте гостем. Гость тоже может начать бой
кампании и принять его результат.

### 2.3 Остановка, перезапуск, сброс

| Действие | Команда |
|----------|---------|
| Остановить сайт (данные сохраняются) | `docker compose --profile local down` |
| Запустить снова | `docker compose --profile local up -d` |
| Пересобрать после обновления кода (`git pull`) | `docker compose --profile local up -d --build` |
| Посмотреть логи сервера | `docker compose logs -f backend` (выход — `Ctrl+C`). Строки — JSON; ошибки: `docker compose logs backend \| Select-String '"ERROR"'` |
| Проверить состояние сервера | `curl.exe http://localhost:8088/api/actuator/health` → `{"status":"UP"}` |
| **Удалить все данные** (кампании, пользователей) | `docker compose --profile local down -v` |

В PowerShell пишите именно `curl.exe`: просто `curl` там — другая команда.

### 2.4 Открыть сайт с телефона в той же Wi-Fi-сети

1. Узнайте IP компьютера: `ipconfig` → «IPv4-адрес», например `192.168.1.20`.
2. В `.env` поменяйте `PUBLIC_URL=http://192.168.1.20:8088` и перезапустите:
   `docker compose --profile local up -d`.
3. На телефоне откройте `http://192.168.1.20:8088`. Если не открывается — разрешите входящие подключения
   на порт 8088 в брандмауэре Windows.

Ограничение: адрес `http://IP` браузер считает незащищённым и не даёт вибрацию, запрет выключения экрана,
установку сайта как приложения и работу без сети (service worker). Бои, кампании и «Копировать» ссылку
работают. Остальные функции проверяйте на сервере (раздел 3) или на компьютере по адресу `localhost`.

---

## 3. Запуск на сервере

### 3.1 Что подготовить

| Что | Требования |
|-----|------------|
| Сервер (VPS) | Ubuntu 24.04 LTS, 2 vCPU, **4 ГБ памяти** (сборка; при 2 ГБ — см. 3.9), 20 ГБ диска, публичный IPv4 |
| Домен | Например, `primal.example.ru` (свой домен или поддомен) |
| Почтовый сервис | SMTP-сервис для рассылки писем с вашего домена, например: Unisender Go, SendPulse, Яндекс 360 для бизнеса, Amazon SES, Brevo, Mailgun |
| SSH-ключ | На своём ПК: `ssh-keygen -t ed25519` (PowerShell); публичный ключ `~\.ssh\id_ed25519.pub` добавьте при создании VPS |

Дальше везде `primal.example.ru` — ваш домен, `203.0.113.10` — IP сервера.

### 3.2 Домен

В панели DNS регистратора добавьте запись **A**: имя `primal` (или `@` для корневого домена) →
`203.0.113.10`. Проверьте через 5–30 минут:

```powershell
nslookup primal.example.ru
```
Ответ должен содержать IP сервера. Без этого сайт не получит HTTPS-сертификат.

### 3.3 Почта

1. Зарегистрируйтесь в почтовом сервисе и подтвердите домен. Сервис покажет DNS-записи — добавьте их у
   регистратора:
   - **SPF** — TXT-запись на домене, например `v=spf1 include:<значение сервиса> ~all`;
   - **DKIM** — TXT- или CNAME-запись с ключом подписи;
   - **DMARC** — TXT-запись `_dmarc` со значением `v=DMARC1; p=none; rua=mailto:you@example.ru`.

   Без SPF и DKIM письма с кодами будут попадать в спам или не доходить.
2. Запишите SMTP-адрес, порт (обычно **587** — STARTTLS, или **465** — SSL), логин и пароль.
   Порт 25 не используйте: многие хостинги его блокируют.

### 3.4 Первичная настройка сервера

Подключитесь с ПК: `ssh root@203.0.113.10`. Дальше команды выполняются на сервере.

```bash
# обновления и нужные пакеты
apt update && apt upgrade -y
apt install -y git ufw unattended-upgrades

# пользователь для сайта (вход — по тому же SSH-ключу)
adduser --disabled-password --gecos "" primal
usermod -aG sudo primal
rsync --archive --chown=primal:primal ~/.ssh /home/primal
echo "primal ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/primal

# брандмауэр: только SSH, HTTP, HTTPS
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

Если у сервера меньше 4 ГБ памяти, добавьте файл подкачки, иначе сборка может упасть:

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

Проверьте ещё панель хостинга: если там свой брандмауэр, откройте в нём порты 80 и 443.

### 3.5 Установка Docker

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
usermod -aG docker primal
```

Это официальный скрипт установки Docker. Вариант через apt-репозиторий описан на
https://docs.docker.com/engine/install/ubuntu/.

Выйдите (`exit`) и зайдите уже пользователем `primal`: `ssh primal@203.0.113.10`. Проверьте:
`docker compose version`.

### 3.6 Развёртывание

```bash
sudo mkdir -p /opt/primal && sudo chown primal:primal /opt/primal
git clone https://github.com/ViktorEvdokimov/primalApp.git /opt/primal
cd /opt/primal/web
cp .env.example .env
chmod 600 .env
```

Если репозиторий приватный: создайте ключ `ssh-keygen -t ed25519 -f ~/.ssh/primal_deploy -N ""`,
добавьте `~/.ssh/primal_deploy.pub` в GitHub → репозиторий → Settings → Deploy keys (только чтение) и
клонируйте так:
`GIT_SSH_COMMAND="ssh -i ~/.ssh/primal_deploy" git clone git@github.com:ViktorEvdokimov/primalApp.git /opt/primal`.

Сгенерируйте три секрета — выполните команду три раза:

```bash
openssl rand -hex 32
```

Откройте настройки (`nano .env`; сохранить — `Ctrl+O`, `Enter`, выйти — `Ctrl+X`) и заполните:

```ini
SITE_ADDRESS=primal.example.ru
HTTP_PORT=80
HTTPS_PORT=443
PUBLIC_URL=https://primal.example.ru

POSTGRES_DB=primal
POSTGRES_USER=primal
POSTGRES_PASSWORD=<секрет 1>

PRIMAL_OTP_PEPPER=<секрет 2>
PRIMAL_SHARE_LINK_KEY=<секрет 3>

SMTP_HOST=<SMTP-адрес сервиса>
SMTP_PORT=587
SMTP_USERNAME=<логин>
SMTP_PASSWORD=<пароль>
SMTP_TLS=starttls
MAIL_FROM=Primal <no-reply@primal.example.ru>
```

Сохраните секреты ещё и в менеджере паролей. `PRIMAL_SHARE_LINK_KEY` особенно важен: если он потеряется
или изменится, все выданные ссылки-приглашения перестанут работать (раздел 3.11).

Запустите сайт. Профиль `local` не указываем, поэтому Mailpit на сервере не запускается:

```bash
docker compose up -d --build
```

### 3.7 Проверка

```bash
docker compose ps                       # у всех сервисов Up (healthy)
docker compose logs web | grep -i certificate   # ищите «certificate obtained successfully»
curl https://primal.example.ru/api/actuator/health   # {"status":"UP"}
```

Откройте `https://primal.example.ru`, войдите со своей настоящей почтой. Если письмо не пришло,
проверьте папку «Спам» и раздел 6. Качество настройки почты можно проверить на https://www.mail-tester.com.

### 3.8 Обновление сайта

```bash
cd /opt/primal/web
bash deploy/backup.sh                   # сначала резервная копия
git pull
docker compose up -d --build
docker image prune -f                   # удалить старые образы
```

Изменения схемы БД применяются автоматически при старте сервера. Откатить их нельзя, поэтому при
неудачном обновлении: `git checkout <предыдущая версия>`, `docker compose up -d --build` и
восстановление копии, сделанной перед обновлением (раздел 3.10).

### 3.9 Если на сервере мало памяти

Сборка Java-сервера требует около 2 ГБ памяти. На слабом сервере образы не собирают, а скачивают готовые:
их собирает GitHub Actions (`.github/workflows/web-release.yml`) при публикации тега `web-v*`.

1. Выпустите версию — на ПК в каталоге репозитория:
   ```bash
   git tag web-v1.0.0
   git push origin web-v1.0.0
   ```
   В GitHub → Actions дождитесь зелёного `web-release`. Образы появятся в GitHub → профиль → Packages:
   `primal-backend` и `primal-web`.
2. Если репозиторий приватный, образы тоже приватные. Создайте в GitHub → Settings → Developer settings →
   Personal access tokens (classic) токен с правом `read:packages` и войдите на сервере:
   `docker login ghcr.io -u <логин GitHub>` (пароль — токен). Для публичных образов вход не нужен.
3. На сервере в `.env` добавьте `IMAGE_TAG=web-v1.0.0` (и `IMAGE_REGISTRY=ghcr.io/<логин в нижнем регистре>`,
   если репозиторий не `viktorevdokimov`) и запускайте без сборки:
   ```bash
   docker compose pull backend web
   docker compose up -d
   ```
   Обновление — новый тег, `IMAGE_TAG` в `.env` и те же две команды.

### 3.10 Резервные копии

Все данные кампаний — в базе данных. Незаконченные бои хранятся в браузерах игроков и в копию не
попадают. Создайте каталог для копий и проверьте, что копия делается:

```bash
mkdir -p /opt/primal/backups
cd /opt/primal/web && bash deploy/backup.sh && ls -lh /opt/primal/backups
```

Настройте ежедневную копию: `crontab -e` и добавьте строку

```
30 3 * * * cd /opt/primal/web && bash deploy/backup.sh >> /opt/primal/backups/backup.log 2>&1
```

Копии сохраняются в `/opt/primal/backups` и хранятся 14 дней. Периодически забирайте их к себе — с ПК:

```powershell
scp primal@203.0.113.10:/opt/primal/backups/*.sql.gz D:\backups\primal\
```

Восстановление (сайт будет недоступен около минуты):

```bash
cd /opt/primal/web
bash deploy/restore.sh /opt/primal/backups/primal-2026-10-01-0330.sql.gz
```

### 3.11 Обслуживание

| Задача | Команда / действие |
|--------|--------------------|
| Логи | `docker compose logs -f backend` / `web` / `postgres` |
| Перезапустить сервер сайта | `docker compose restart backend` |
| Место на диске | `df -h`, `docker system df`; очистка — `docker image prune -f` |
| Сменить пароль БД | `POSTGRES_PASSWORD` действует только при первом создании БД. Смена: `docker compose exec postgres psql -U primal -c "ALTER USER primal WITH PASSWORD '<новый>'"`, затем новое значение в `.env` и `docker compose up -d` |
| Сменить `PRIMAL_OTP_PEPPER` | Перестанут действовать только уже отправленные коды |
| Сменить `PRIMAL_SHARE_LINK_KEY` | Все ссылки-приглашения станут недействительными; владельцы заново копируют ссылки в окне «Поделиться». Входы по устройствам не затрагиваются |
| Выкинуть все устройства всех пользователей | Сейчас только через БД; понадобится редко — делайте с резервной копией |

---

## 4. Режим разработки (для программиста)

В этом режиме в Docker работают только база и Mailpit, а сервер и сайт запускаются из IDE с мгновенной
перезагрузкой при изменениях.

**Установить дополнительно:** любой JDK 17 или новее (для запуска Gradle), Node.js 24 LTS
(https://nodejs.org), IntelliJ IDEA. JDK 25 для сборки Gradle при первом запуске скачает сам в
`%USERPROFILE%\.gradle\jdks`. На Windows запускайте `gradlew.bat`: скрипт `gradlew` из Git Bash не работает.

Полный стек из раздела 2 перед этим остановите: `docker compose --profile local down` — иначе
Mailpit займёт тот же порт.

```powershell
cd D:\projekts\promptLearning\primalApp\web
docker compose -f docker-compose.dev.yml up -d        # PostgreSQL :5432, Mailpit :1025 / :8025

cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"   # сервер на :8080
```
В IntelliJ: откройте `web/backend` как Gradle-проект и запустите `PrimalApplication` с профилем `dev`.

Во втором окне PowerShell:

```powershell
cd D:\projekts\promptLearning\primalApp\web\frontend
npm ci
npm run dev                                           # сайт на http://localhost:5173
```

| Адрес | Что там |
|-------|---------|
| http://localhost:5173 | Сайт (запросы `/api` уходят на :8080) |
| http://localhost:8080/swagger-ui.html | Описание API с возможностью вызова |
| http://localhost:8025 | Mailpit — письма с кодами |

| Действие | Команда |
|----------|---------|
| Обновить API-клиент фронтенда после изменения API | `npm run api:generate` (сервер запущен с профилем `dev`: `.\gradlew.bat bootRun --args='--spring.profiles.active=dev'`); результат `src/api/generated` сохранить в git |
| Тесты сервера (нужен запущенный Docker Desktop) | `.\gradlew.bat test` |
| Тесты фронтенда | `npm test` |
| E2E-тесты: подготовка, один раз | `cd web\e2e; poetry install; poetry run playwright install chromium` |
| E2E-тесты (при запущенном стеке раздела 2) | `cd web\e2e; poetry run pytest` — отчёт Allure в `allure-results`; адреса сайта и Mailpit — в `config.json` |

---

## 5. Переменные окружения

Файл `web/.env` — рядом с `docker-compose.yml`. В git не попадает.

| Переменная | Локально (`.env.example`) | На сервере | Назначение |
|------------|---------------------------|------------|------------|
| `SITE_ADDRESS` | `:80` | `primal.example.ru` | Адрес для Caddy. Домен включает HTTPS автоматически |
| `HTTP_PORT` | `8088` | `80` | Порт HTTP на компьютере или сервере |
| `HTTPS_PORT` | `8443` | `443` | Порт HTTPS |
| `PUBLIC_URL` | `http://localhost:8088` | `https://primal.example.ru` | Адрес для пользователей: из него строятся ссылки-приглашения. `https://` включает защищённые cookie и строгую проверку секретов |
| `POSTGRES_DB` | `primal` | `primal` | Имя БД |
| `POSTGRES_USER` | `primal` | `primal` | Пользователь БД |
| `POSTGRES_PASSWORD` | `primal-local-password` | секрет | Пароль БД (действует при первом создании БД) |
| `PRIMAL_OTP_PEPPER` | `local-dev-otp-pepper-change-me` | секрет, ≥ 32 символов | Ключ хеширования кодов входа |
| `PRIMAL_SHARE_LINK_KEY` | `local-dev-share-key-change-me` | секрет, ≥ 32 символов, **не менять** | Ключ подписи ссылок-приглашений |
| `SMTP_HOST` | `mailpit` | адрес сервиса | SMTP-сервер |
| `SMTP_PORT` | `1025` | `587` или `465` | Порт SMTP |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | пусто | от сервиса | Вход в SMTP |
| `SMTP_TLS` | `none` | `starttls` (587) или `ssl` (465) | Шифрование SMTP |
| `MAIL_FROM` | `Primal <no-reply@primal.local>` | `Primal <no-reply@ваш-домен>` | Отправитель писем |
| `PRIMAL_CODES_PER_IP_HOUR` | `500` (для E2E) | не задавать (20) | Писем с кодом входа с одного IP в час |
| `PRIMAL_VERIFICATIONS_PER_IP_HOUR` | `1000` (для E2E) | не задавать (60) | Проверок кода с одного IP в час |
| `PRIMAL_JOINS_PER_IP_HOUR` | `1000` (для E2E) | не задавать (30) | Входов по ссылкам-приглашениям с одного IP в час |
| `IMAGE_TAG` | не задан (`local`) | не задан или версия `web-v…` (3.9) | Тег образов: `local` — собранные на месте, версия — скачанные из GHCR |
| `IMAGE_REGISTRY` | не задан | не задан или `ghcr.io/<логин>` (3.9) | Откуда скачивать образы; по умолчанию `ghcr.io/viktorevdokimov` |
| `SPRING_PROFILES_ACTIVE` | не задан (`prod`) | не задан (`prod`) | Профиль сервера: `prod` — логи в формате JSON с номером запроса `requestId` |

Если `PUBLIC_URL` начинается с `https://`, а секреты остались из примера или короче 32 символов,
сервер не запустится и напишет в логе, что заменить.

---

## 6. Типовые проблемы

| Симптом | Причина и решение |
|---------|-------------------|
| `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified` | Docker Desktop не запущен. Запустите его и дождитесь «Engine running» |
| `Bind for 0.0.0.0:8088 failed: port is already allocated` | Порт занят. Поменяйте `HTTP_PORT` в `.env` (и порт в `PUBLIC_URL`) |
| Сборка падает: `/bin/sh^M: bad interpreter` или `gradlew: not found` | Файлы скачались с окончаниями строк Windows. Проверьте, что есть `web/.gitattributes`, затем удалите `web/backend/gradlew` и выполните `git checkout -- web/backend/gradlew` |
| Сборка на сервере обрывается: `Killed`, код 137 | Не хватает памяти: добавьте подкачку (3.4) или используйте готовые образы (3.9) |
| `backend` в состоянии `unhealthy` или перезапускается | `docker compose logs backend`. Частые причины: секреты из примера при `https` (заменить), неверный пароль БД (3.11) |
| Локально код не приходит | Письма не уходят наружу — смотрите http://localhost:8025 |
| На сервере код не приходит | Папка «Спам»; `docker compose logs backend \| grep -i mail`; SMTP-настройки в `.env`; записи SPF и DKIM (3.3). После исправления — `docker compose up -d` |
| «Слишком много запросов» при входе | Защита от перебора: 1 письмо в минуту и 5 в час на адрес. Подождите указанное время |
| HTTPS не работает, сертификат не выдан | `docker compose logs web`. Проверьте: DNS-запись указывает на сервер (3.2); порты 80 и 443 открыты в `ufw` и в панели хостинга; в `SITE_ADDRESS` только домен, без `https://`. Не перезапускайте контейнер десятки раз: у Let's Encrypt есть лимиты выдачи |
| Ссылка-приглашение ведёт на `localhost` | На сервере не заменён `PUBLIC_URL` — исправьте и выполните `docker compose up -d` |
| С телефона по `http://IP` нет вибрации, установки приложения и работы без сети | Браузеры дают эти функции только по HTTPS (или на `localhost`) — проверяйте на сервере |
| Все устройства снова требуют вход | Удалена или пересоздана БД. Восстановите резервную копию (3.10) |
| Пропал незаконченный бой | Бой хранится только в том браузере, где его начали. Он пропадает при очистке данных сайта в браузере и не виден в другом браузере или в окне инкогнито |
| Результат боя кампании «не отправлен» | Не было сети при «Принять». Результат хранится в этом браузере: если экран наград открыт, он отправится сам, когда сеть появится; иначе в меню или на листе кампании нажмите «Отправить результат» |
| Сборка `docker compose … --build` стоит на `npm ci` или скачивании образов | Сеть внутри Docker Desktop зависла (бывает после его аварийного перезапуска). Перезапустите Docker Desktop (значок в трее → Restart) и повторите команду |
| Сообщение об ошибке на сайте, нужно найти её в логах | Номер запроса — в заголовке ответа `X-Request-Id` (браузер → F12 → Network). В логах: `docker compose logs backend \| Select-String '<номер>'` |
| «Кампания изменилась» при отправке результата | Пока шёл бой, другой участник принял победу в своём бою — первая принятая победа закрывает главу — или изменил главу или задание. Отклоните результат и при необходимости внесите награды вручную на листе кампании |
| «Уже идёт бой» при подготовке боя кампании | Другой участник начал бой этой кампании. Это предупреждение: начать свой бой можно, но главу засчитает первая принятая победа. Забытую отметку можно снять в истории боёв на листе кампании |
