<template>
  <div class="p-2 app-container content-product-page">
    <PageHeading
      title="内容生产协同"
      subtitle="轻量产品与SKU：为事实快照与开工包提供可追溯的产品标识"
      module="content"
    />
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>产品检索</h3>
            </div>
          </div>
        </template>
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="关键字" prop="keyword">
            <el-input
              v-model="queryParams.keyword"
              placeholder="产品编码 / 名称 / SKU"
              clearable
              style="width: 240px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Product &amp; SKU</span>
            <h3>产品与SKU</h3>
            <p>共 {{ total }} 条记录；产品编码唯一，版本用于产品变更后的影响面追溯。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['content:product:add']" type="primary" plain icon="Plus" @click="handleAdd">
              新增
            </el-button>
            <el-button
              v-hasPermi="['content:product:edit']"
              type="success"
              plain
              icon="Edit"
              :disabled="single"
              @click="handleUpdate()"
            >
              修改
            </el-button>
            <el-button
              v-hasPermi="['content:product:remove']"
              type="danger"
              plain
              icon="Delete"
              :disabled="multiple"
              @click="handleDelete()"
            >
              删除
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="getList"></right-toolbar>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="productList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="产品编码" align="center" prop="productCode" width="160" show-overflow-tooltip />
        <el-table-column label="产品名称" align="center" prop="productName" min-width="180" show-overflow-tooltip />
        <el-table-column label="SKU编码" align="center" prop="skuCode" width="150" show-overflow-tooltip />
        <el-table-column label="SKU名称" align="center" prop="skuName" width="160" show-overflow-tooltip />
        <el-table-column label="品类" align="center" prop="category" width="120" show-overflow-tooltip />
        <el-table-column label="品牌" align="center" prop="brand" width="100" show-overflow-tooltip />
        <el-table-column label="二级分类" align="center" prop="subCategory" width="160" show-overflow-tooltip />
        <el-table-column label="产品经理" align="center" prop="productManager" width="110" show-overflow-tooltip />
        <el-table-column label="尺寸" align="center" prop="sizeSpec" width="180" show-overflow-tooltip />
        <el-table-column label="结构/工艺" align="center" prop="craft" width="150" show-overflow-tooltip />
        <el-table-column label="版本" align="center" prop="version" width="100" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-hasPermi="['content:product:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                v-hasPermi="['content:product:remove']"
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增或修改产品对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px" append-to-body>
      <el-form ref="productFormRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="产品编码" prop="productCode">
              <el-input v-model="form.productCode" placeholder="唯一标识，如 JF-0001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="SKU编码" prop="skuCode">
              <el-input v-model="form.skuCode" placeholder="可留空（产品级）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SKU名称" prop="skuName">
              <el-input v-model="form.skuName" placeholder="请输入SKU名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="品类" prop="category">
              <el-input v-model="form.category" placeholder="如 积木花" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌" prop="brand">
              <el-input v-model="form.brand" placeholder="如 趣往" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="二级分类" prop="subCategory">
              <el-input v-model="form.subCategory" placeholder="如 解构花园-静态花" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品经理" prop="productManager">
              <el-input v-model="form.productManager" placeholder="请输入产品经理" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="尺寸" prop="sizeSpec">
              <el-input v-model="form.sizeSpec" type="textarea" :rows="2" placeholder="如 257.60*149.30；多形态换行" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格" prop="price">
              <el-input v-model="form.price" placeholder="单位：元，可留空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="结构/工艺" prop="craft">
          <el-input v-model="form.craft" type="textarea" :rows="2" placeholder="如 UV+喷漆、喷漆+镀铬；多工艺换行" />
        </el-form-item>
        <el-form-item label="产品图" prop="productImage">
          <el-input v-model="form.productImage" placeholder="对象存储键或图片 URL（业务库不存文件本体）" />
        </el-form-item>
        <el-form-item label="主推说明" prop="mainPush">
          <el-input v-model="form.mainPush" type="textarea" :rows="2" placeholder="如「作为单枝花售卖时，为动转静，静态化的形态售卖」" />
        </el-form-item>
        <el-form-item label="设计灵感" prop="designInspiration">
          <el-input v-model="form.designInspiration" type="textarea" :rows="3" placeholder="请输入设计灵感" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="产品版本" prop="version">
              <el-input v-model="form.version" placeholder="如 V2，用于影响面追溯" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
              {{ dict.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { CpProductForm, CpProductQuery, CpProductVO } from '@/api/content/product/types';
import { addProduct, delProduct, getProduct, listProduct, updateProduct } from '@/api/content/product';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useSearchToggle } from '@/hooks/form/useSearchToggle';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import modal from '@/plugins/modal';
import { useDict } from '@/utils/dict';
import { parseTime } from '@/utils/ruoyi';

defineOptions({ name: 'ContentProduct' });

const { sys_normal_disable } = toRefs<any>(useDict('sys_normal_disable'));

const productList = ref<CpProductVO[]>([]);
const { loading, withLoading } = useLoading(true);
const { showSearch } = useSearchToggle();
const { ids, single, multiple, handleSelectionChange } = useTableSelection<CpProductVO>(item => item.productId!);
const total = ref(0);
const productFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const initFormData: CpProductForm = {
  productId: undefined,
  productCode: '',
  productName: '',
  skuCode: '',
  skuName: '',
  category: '',
  brand: '',
  subCategory: '',
  productImage: '',
  mainPush: '',
  productManager: '',
  sizeSpec: '',
  price: '',
  craft: '',
  designInspiration: '',
  version: '',
  status: '0',
  remark: ''
};

const data = reactive<PageData<CpProductForm, CpProductQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: '',
    status: undefined,
    params: {}
  },
  rules: {
    productCode: [{ required: true, message: '产品编码不能为空', trigger: 'blur' }],
    productName: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<CpProductForm, CpProductQuery>>(data);
const { dialog, resetForm, openDialog, showDialog, closeDialog } = useFormDialog({
  form,
  formRef: productFormRef,
  initialFormData: initFormData
});
const { resetQuery } = useSearchReset({
  queryFormRef,
  queryParams,
  pageNumKey: 'pageNum',
  afterReset: () => handleQuery()
});

/** 查询产品列表 */
const getList = async () => {
  await withLoading(async () => {
    const res = await listProduct(queryParams.value);
    productList.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  });
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 取消按钮 */
const cancel = () => {
  closeDialog();
  resetForm();
};

/** 新增按钮操作 */
const handleAdd = () => {
  openDialog('新增产品');
};

/** 修改按钮操作 */
const handleUpdate = async (row?: Partial<CpProductVO>) => {
  resetForm();
  const productId = row?.productId || ids.value[0];
  const res = await getProduct(productId!);
  Object.assign(form.value, res.data);
  showDialog('修改产品');
};

/** 提交按钮 */
const submitForm = () => {
  productFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.productId ? await updateProduct(form.value) : await addProduct(form.value);
      modal.msgSuccess('操作成功');
      closeDialog();
      await getList();
    }
  });
};

/** 删除按钮操作 */
const handleDelete = async (row?: Partial<CpProductVO>) => {
  const productIds = row?.productId || ids.value;
  await modal.confirm('是否确认删除产品编号为"' + productIds + '"的数据项？');
  await delProduct(productIds);
  await getList();
  modal.msgSuccess('删除成功');
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;
</style>
