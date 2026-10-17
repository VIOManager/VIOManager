<script setup lang="ts">
/**
 * 人员新建/编辑弹窗（A 线 M03/M04）
 * 校验：学号可空、新建查重 3001、编辑拒改 3002（输入框在编辑态禁用，双重保险）
 * 学院→专业级联来自 O06 静态映射；技能多选来自 useDict（红线：不硬编码）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiColleges } from '@/api/org'
import type { CollegeItem } from '@/api/org'
import { apiCreateMember, apiMemberDetail, apiUpdateMember } from '@/api/member'
import type { MemberForm, MemberType } from '@/api/member'
import { useDict } from '@/composables/useDict'
import { MEMBER_TYPE_LABEL, GENDER_LABEL } from '@/config/labels'

const visible = defineModel<boolean>({ required: true })
/** 空串=新建；有值=编辑 */
const props = defineProps<{ memberId: string; orgOptions: { id: string; name: string; level: number }[] }>()
const emit = defineEmits<{ saved: [] }>()

/** 性别枚举值与后端 PersonCreateDTO @Pattern 一致（MALE/FEMALE/UNKNOWN），中文翻译走 GENDER_LABEL */
const GENDER_OPTIONS = ['MALE', 'FEMALE', 'UNKNOWN']

const empty = () => ({
  name: '', studentNo: '', phone: '', college: '', major: '', className: '',
  gender: 'UNKNOWN', nation: '', email: '', memberType: 'STUDENT' as MemberType, orgId: '', skills: [] as string[],
  idCard: ''
})

const form = reactive(empty())
const submitting = ref(false)
const colleges = ref<CollegeItem[]>([])
const majors = ref<string[]>([])
const { selectable: skillOptions } = useDict('SKILL')

watch(visible, async (v) => {
  if (!v) return
  Object.assign(form, empty())
  majors.value = []
  if (colleges.value.length === 0) colleges.value = await apiColleges()
  if (props.memberId) {
    const d = await apiMemberDetail(props.memberId)
    form.name = d.name
    form.studentNo = d.studentNo ?? ''
    form.phone = d.phone ?? ''
    form.college = d.college
    form.major = d.major ?? ''
    form.className = d.className
    form.gender = d.gender ?? 'UNKNOWN'
    form.nation = d.nation ?? ''
    form.email = d.email ?? ''
    form.memberType = d.memberType
    form.orgId = d.orgId ?? ''
    form.skills = [...d.skills]
    onCollegeChange()
    form.major = d.major ?? ''
  }
})

function onCollegeChange() {
  form.major = ''
  majors.value = []
  for (const c of colleges.value) {
    if (c.college === form.college) {
      majors.value = c.majors
      break
    }
  }
}

// 组装提交体：可空字段的空串一律不传——后端 @Pattern/@Long 对空串直接报 1001，
// 且编辑是部分更新语义（不传=不改这一项）；编辑还必须把 id 放进 body（P02 契约）
function buildBody(): MemberForm {
  const body: MemberForm = {
    name: form.name.trim(),
    studentNo: form.studentNo.trim(),
    gender: form.gender,
    memberType: form.memberType,
    skills: form.skills
  }
  if (form.phone.trim()) body.phone = form.phone.trim()
  if (form.college) body.college = form.college
  if (form.major.trim()) body.major = form.major.trim()
  if (form.className.trim()) body.className = form.className.trim()
  if (form.nation.trim()) body.nation = form.nation.trim()
  if (form.email.trim()) body.email = form.email.trim()
  if (form.orgId) body.orgId = form.orgId
  if (form.idCard.trim()) body.idCard = form.idCard.trim()
  return body
}

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('姓名必填')
    return
  }
  if (form.idCard.trim() && !/^\d{17}[0-9Xx]$/.test(form.idCard.trim())) {
    ElMessage.warning('身份证号须为 18 位数字（末位可为 X）')
    return
  }
  submitting.value = true
  try {
    if (props.memberId) {
      await apiUpdateMember(props.memberId, { ...buildBody(), id: props.memberId })
      ElMessage.success('档案已更新')
    } else {
      await apiCreateMember(buildBody())
      ElMessage.success('人员已建档')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 3001 学号重复 / 3002 学号不可改 提示已由 http 拦截器弹出
  } finally {
    submitting.value = false
  }
}

</script>

<template>
  <el-dialog v-model="visible" :title="memberId ? '编辑档案' : '新建人员'" width="560px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="学号/工号">
        <el-input v-model="form.studentNo" :disabled="!!memberId" placeholder="可空；建档后不可修改" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.memberType" style="width: 100%">
          <el-option v-for="(label, v) in MEMBER_TYPE_LABEL" :key="v" :value="v" :label="label" />
        </el-select>
      </el-form-item>
      <el-form-item label="学院">
        <el-select v-model="form.college" style="width: 100%" @change="onCollegeChange">
          <el-option v-for="c in colleges" :key="c.college" :value="c.college" :label="c.college" />
        </el-select>
      </el-form-item>
      <el-form-item label="专业">
        <el-select v-model="form.major" style="width: 100%" :disabled="majors.length === 0">
          <el-option v-for="m in majors" :key="m" :value="m" :label="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级"><el-input v-model="form.className" placeholder="如：软工23-2班" /></el-form-item>
      <el-form-item label="性别 / 民族">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-select v-model="form.gender" style="width: 40%">
            <el-option v-for="g in GENDER_OPTIONS" :key="g" :value="g" :label="GENDER_LABEL[g]" />
          </el-select>
          <el-input v-model="form.nation" placeholder="民族" style="width: 60%" />
        </div>
      </el-form-item>
      <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="身份证号">
        <el-input v-model="form.idCard" :placeholder="memberId ? '不填保持原值（敏感加密存储）' : '18 位，选填（敏感加密存储）'" />
      </el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      <el-form-item label="所属组织">
        <el-select v-model="form.orgId" style="width: 100%">
          <el-option v-for="o in orgOptions" :key="o.id" :value="o.id" :label="'　'.repeat(o.level - 1) + o.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="技能标签">
        <el-select v-model="form.skills" multiple style="width: 100%">
          <el-option v-for="s in skillOptions" :key="s.id" :value="s.label" :label="s.label" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
