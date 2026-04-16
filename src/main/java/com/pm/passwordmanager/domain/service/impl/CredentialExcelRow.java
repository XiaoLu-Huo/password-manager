package com.pm.passwordmanager.domain.service.impl;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EasyExcel 行数据模型，用于凭证的导入导出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialExcelRow {

    @ExcelProperty("账户名称")
    private String accountName;

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("密码")
    private String password;

    @ExcelProperty("URL")
    private String url;

    @ExcelProperty("备注")
    private String notes;

    @ExcelProperty("标签")
    private String tags;
}
