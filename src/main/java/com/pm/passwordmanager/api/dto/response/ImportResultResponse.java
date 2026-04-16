package com.pm.passwordmanager.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导入结果")
public class ImportResultResponse {

    @Schema(description = "成功导入的凭证数量")
    private int importedCount;

    @Schema(description = "跳过的凭证数量")
    private int skippedCount;

    @Schema(description = "覆盖的凭证数量")
    private int overwrittenCount;

    @Schema(description = "总行数")
    private int totalCount;
}
