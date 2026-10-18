package com.viomanager.org.service;

import com.viomanager.org.dto.OrgSaveDTO;
import com.viomanager.org.vo.CollegeVO;
import com.viomanager.org.vo.OrgDetailVO;
import com.viomanager.org.vo.OrgNodeVO;
import java.util.List;

/**
 * 组织管理服务（A 线契约 O01-O06）。
 * 数据范围：超管全平台；部门管理员仅本部门子树（树/详情/写操作全部同口径）。
 */
public interface OrgService {

    /** O01 组织树：超管全平台，部门管理员仅本部门子树 */
    List<OrgNodeVO> tree();

    /** O05 组织详情（可见性同树接口） */
    OrgDetailVO detail(Long id);

    /** O02 创建组织，返回新组织 ID；父组织必须可见（部门管理员只能建在自己子树下） */
    Long create(OrgSaveDTO dto);

    /** O03 编辑；改上级时校验防循环（3006）并级联重算子树 path 与 level */
    void update(Long id, OrgSaveDTO dto);

    /** O04 删除：有子组织拒绝（3007），删除后该组织人员 org_id 置空 */
    void delete(Long id);

    /** O06 学院-专业静态映射（内置常量，不查库） */
    List<CollegeVO> colleges();
}
