package cn.iocoder.yudao.module.campus.common.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M00.02 验收（A 级）：代码中的错误码登记表与契约文档逐条一致，避免「文档一套、实现一套」。
 *
 * <p>契约文件位于仓库根 {@code contracts/openapi/campus-error-codes.csv}；Surefire 的工作目录是
 * 模块目录 {@code backend/campus-common}，因此相对路径为 {@code ../../contracts/...}。
 */
class CampusErrorCodesContractTest {

    private static final Path CONTRACT_FILE = Paths.get("..", "..", "contracts", "openapi",
            "campus-error-codes.csv");

    @Test
    void errorRegistry_shouldMatchFrozenContractFile() throws IOException {
        Path path = CONTRACT_FILE.toAbsolutePath().normalize();
        assertThat(path).as("契约文件缺失，错误码不允许脱离文档演进").exists();

        List<String[]> rows = parse(Files.readAllLines(path, StandardCharsets.UTF_8));

        assertThat(rows).hasSize(CampusErrorCodes.ALL.size());
        Map<String, String[]> byName = new HashMap<>();
        for (String[] row : rows) {
            assertThat(byName.put(row[0], row)).as("错误码名称重复：%s", row[0]).isNull();
        }
        for (CampusErrorSpec spec : CampusErrorCodes.ALL) {
            String[] row = byName.get(spec.getName());
            assertThat(row).as("契约文件缺少错误码：%s", spec.getName()).isNotNull();
            assertThat(Integer.parseInt(row[1])).isEqualTo(spec.getCode());
            assertThat(Integer.parseInt(row[2])).isEqualTo(spec.getHttpStatusValue());
            assertThat(CampusRetryAction.valueOf(row[3])).isEqualTo(spec.getRetryAction());
            assertThat(row[4]).isEqualTo(spec.getMessagePattern());
        }
        assertThat(rows.stream().map(row -> row[1]).collect(Collectors.toSet()))
                .as("数值编码不允许重复").hasSize(rows.size());
    }

    /**
     * 解析 {@code name,code,http_status,retry_action,"message"} 五列；message 始终带引号。
     */
    private static List<String[]> parse(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("name,"))
                .map(String::trim)
                .map(line -> line.split(",", 5))
                .map(columns -> new String[]{
                        columns[0].trim(),
                        columns[1].trim(),
                        columns[2].trim(),
                        columns[3].trim(),
                        columns[4].trim().replaceAll("^\"|\"$", "")})
                .toList();
    }

}
