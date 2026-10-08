package com.primal.info;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.AuthHelper;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Инфо: ключевые слова, реакции, жетоны (qa № 142)")
class InfoIT extends IntegrationTest {

    /** 1×1 PNG. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    private Cookie admin;
    private Cookie player;

    @BeforeEach
    void users() throws Exception {
        admin = auth.login("admin");
        player = auth.login("player");
        jdbc.update("insert into app_admin (user_id) select id from app_user where login = ?", AuthHelper.loginOf("admin"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from info_entry");
        jdbc.update("delete from info_image");
        deleteIdentityData();
    }

    private ResultActions create(Cookie who, String section, String title, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/info/entries").with(xsrf()).cookie(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"section\": \"%s\", \"title\": \"%s\", \"body\": \"%s\"}".formatted(section, title, body)));
    }

    private long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    @DisplayName("без входа: три раздела по порядку меню, статьи по алфавиту; ETag")
    void publicView() throws Exception {
        // подготовка
        create(admin, "KEYWORDS", "Пометка", "Текст.").andExpect(status().isCreated());
        create(admin, "KEYWORDS", "Берсерк", "Текст.").andExpect(status().isCreated());
        create(admin, "TOKENS", "Камень", "Жетон.").andExpect(status().isCreated());

        // вызов и проверка
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, public"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.sections[*].code", contains("KEYWORDS", "REACTIONS", "TOKENS")))
                .andExpect(jsonPath("$.sections[0].title").value("Ключевые слова"))
                .andExpect(jsonPath("$.sections[0].entries[*].title", contains("Берсерк", "Пометка")))
                .andExpect(jsonPath("$.sections[1].entries").isEmpty())
                .andExpect(jsonPath("$.sections[2].entries[0].title").value("Камень"));
    }

    @Test
    @DisplayName("администратор: правка, перенос в другой раздел, повтор названия — 400, удаление")
    void crud() throws Exception {
        // подготовка
        long id = id(create(admin, "KEYWORDS", "Берсерк", "Старый текст."));

        // вызов и проверка
        mockMvc.perform(put("/api/v1/admin/info/entries/" + id).with(xsrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\": \"TOKENS\", \"title\": \"  Ярость  \", \"body\": \"Новый\\n\\n(См. также «Берсерк» .)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.section").value("TOKENS"))
                .andExpect(jsonPath("$.title").value("Ярость"));
        create(admin, "TOKENS", "ярость", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        create(admin, "KEYWORDS", " ", "").andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/admin/info/entries/" + id).with(xsrf()).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.sections[2].entries").isEmpty());
    }

    @Test
    @DisplayName("картинка: PNG загружается и отдаётся с долгим кэшем; не картинка — 400; замена и удаление")
    void image() throws Exception {
        // подготовка
        long id = id(create(admin, "TOKENS", "Камень", "Жетон."));
        String path = "/api/v1/admin/info/entries/" + id + "/image";

        // вызов
        String url = JsonPath.read(mockMvc.perform(multipart(HttpMethod.PUT, path)
                        .file(new MockMultipartFile("file", "stone.png", "image/png", PNG)).with(xsrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("/api/v1/info/images/")))
                .andReturn().getResponse().getContentAsString(), "$.imageUrl");

        // проверка
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(multipart(HttpMethod.PUT, path)
                        .file(new MockMultipartFile("file", "evil.png", "image/png", "<svg/>".getBytes(StandardCharsets.UTF_8)))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Картинка — PNG, JPEG, WebP или GIF."));
        mockMvc.perform(delete(path).with(xsrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isEmpty());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("импорт из файла правил: новые статьи добавлены, существующие не тронуты, без раздела — 400")
    void importKeywords() throws Exception {
        // подготовка
        create(admin, "KEYWORDS", "Первое слово", "Правка администратора.").andExpect(status().isCreated());
        String rules = "# Правила\n##### Ключевые слова\n**Первое слово**\nИз файла.\n\n**Второе слово**\nТекст.\n#### Указатель\n";

        // вызов и проверка
        mockMvc.perform(multipart("/api/v1/admin/info/import")
                        .file(new MockMultipartFile("file", "rules.md", "text/markdown", rules.getBytes(StandardCharsets.UTF_8)))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(jsonPath("$.sections[0].entries[*].title", contains("Второе слово", "Первое слово")))
                .andExpect(jsonPath("$.sections[0].entries[1].body").value("Правка администратора."));
        mockMvc.perform(multipart("/api/v1/admin/info/import")
                        .file(new MockMultipartFile("file", "x.md", "text/markdown", "# Нет раздела".getBytes(StandardCharsets.UTF_8)))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("не администратору правка недоступна — 404")
    void hidden() throws Exception {
        // вызов и проверка
        create(player, "KEYWORDS", "Берсерк", "").andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/admin/info/import")
                        .file(new MockMultipartFile("file", "rules.md", "text/markdown", new byte[] {1}))
                        .with(xsrf()).cookie(player))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("символы реакций — без названия (переданное не сохраняется), в порядке добавления; перенос в раздел с названием — нужно название")
    void reactionsWithoutTitle() throws Exception {
        // подготовка
        long first = id(create(admin, "REACTIONS", "Лишнее название", "Монстр бьёт сильнее."));
        long second = id(mockMvc.perform(post("/api/v1/admin/info/entries").with(xsrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"section\": \"REACTIONS\", \"title\": null, \"body\": \"Ахиллесова пята.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").isEmpty()));

        // вызов и проверка
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(jsonPath("$.sections[1].titled").value(false))
                .andExpect(jsonPath("$.sections[0].titled").value(true))
                .andExpect(jsonPath("$.sections[1].entries[*].id", contains((int) first, (int) second)))
                .andExpect(jsonPath("$.sections[1].entries[0].title").isEmpty());
        mockMvc.perform(put("/api/v1/admin/info/entries/" + first).with(xsrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\": \"KEYWORDS\", \"title\": null, \"body\": \"Текст.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mockMvc.perform(put("/api/v1/admin/info/entries/" + first).with(xsrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\": \"KEYWORDS\", \"title\": \"Сила\", \"body\": \"Текст.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Сила"));
    }

    @Autowired
    private InfoSeeder seeder;

    @Test
    @DisplayName("выгрузка и «Заменить всё из выгрузки»: статьи с картинками и реакции без названия переносятся, старые удаляются")
    void exportAndRestore() throws Exception {
        // подготовка
        long stone = id(create(admin, "TOKENS", "Камень", "Жетон."));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/info/entries/" + stone + "/image")
                .file(new MockMultipartFile("file", "stone.png", "image/png", PNG)).with(xsrf()).cookie(admin));
        create(admin, "REACTIONS", null, "Разворот.").andExpect(status().isCreated());
        byte[] snapshot = mockMvc.perform(get("/api/v1/admin/info/export").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"default-info.json\""))
                .andExpect(jsonPath("$.format").value(1))
                .andExpect(jsonPath("$.entries[0].image.contentType").value("image/png"))
                .andExpect(jsonPath("$.entries[1].title").isEmpty())
                .andReturn().getResponse().getContentAsByteArray();
        create(admin, "KEYWORDS", "Лишнее", "Удалится.").andExpect(status().isCreated());

        // вызов
        mockMvc.perform(multipart("/api/v1/admin/info/restore")
                        .file(new MockMultipartFile("file", "default-info.json", "application/json", snapshot))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored").value(2));

        // проверка
        String url = JsonPath.read(mockMvc.perform(get("/api/v1/info"))
                .andExpect(jsonPath("$.sections[0].entries").isEmpty())
                .andExpect(jsonPath("$.sections[1].entries[0].body").value("Разворот."))
                .andExpect(jsonPath("$.sections[2].entries[0].title").value("Камень"))
                .andReturn().getResponse().getContentAsString(), "$.sections[2].entries[0].imageUrl");
        mockMvc.perform(get(url)).andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("неверная выгрузка — 400, «Инфо» не меняется; не администратору — 404")
    void restoreInvalid() throws Exception {
        // подготовка
        create(admin, "KEYWORDS", "Берсерк", "Текст.").andExpect(status().isCreated());
        String bad = """
                {"format": 1, "entries": [{"section": "TOKENS", "title": "Камень", "body": "",
                  "image": {"contentType": "image/png", "data": "PHN2Zz48L3N2Zz4="}}]}""";

        // вызов и проверка
        mockMvc.perform(multipart("/api/v1/admin/info/restore")
                        .file(new MockMultipartFile("file", "x.json", "application/json", bad.getBytes(StandardCharsets.UTF_8)))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Статья 1: картинка — не PNG, JPEG, WebP или GIF."));
        mockMvc.perform(multipart("/api/v1/admin/info/restore")
                        .file(new MockMultipartFile("file", "x.json", "application/json", "не json".getBytes(StandardCharsets.UTF_8)))
                        .with(xsrf()).cookie(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/info/export").cookie(player)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.sections[0].entries[0].title").value("Берсерк"));
    }

    @Test
    @DisplayName("при запуске: пустое «Инфо» заполняется файлом по умолчанию, заполненное — не трогается")
    void seedOnlyEmpty() throws Exception {
        // подготовка
        String file = """
                {"format": 1, "entries": [{"section": "KEYWORDS", "title": "Берсерк", "body": "Из файла.", "image": null}]}""";
        ByteArrayResource resource = new ByteArrayResource(file.getBytes(StandardCharsets.UTF_8));

        // вызов и проверка
        assertThat(seeder.seed(resource)).isEqualTo(1);
        assertThat(seeder.seed(resource)).isZero();
        mockMvc.perform(get("/api/v1/info")).andExpect(jsonPath("$.sections[0].entries[*].title", contains("Берсерк")));
    }

    @Test
    @DisplayName("файл по умолчанию проекта (info/default-info.json) загружается без ошибок")
    void defaultFile() throws Exception {
        // подготовка
        ClassPathResource resource = new ClassPathResource(InfoSeeder.DEFAULT_FILE);
        assumeTrue(resource.exists(), "файла по умолчанию ещё нет");

        // вызов и проверка
        assertThat(seeder.seed(resource)).isPositive();
    }
}
