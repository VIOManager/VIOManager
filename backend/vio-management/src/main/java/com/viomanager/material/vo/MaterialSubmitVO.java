package com.viomanager.material.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 材料提交出参（MS02）：只回新版本号，其余进度信息以队伍提交概览（MS01）为准。
 */
@Data
@Accessors(chain = true)
public class MaterialSubmitVO {

    /** 新版本号：首次提交为 1，每次覆盖 +1 */
    private Integer version;
}
