<template>
  <FloralLogin>
    <!-- 原项目的语言选择器，继续使用工程 i18n（组件默认按钮只切换组件自身文案） -->
    <template #language>
      <lang-select />
    </template>

    <!--
      原登录表单原样放进 #form 插槽：
      ref / model / rules / 全部字段 / 条件渲染 / 事件 / 提交按钮全部保留，
      只去掉与组件重复的外层标题与卡片（组件已包含标题与卡片）。
    -->
    <template #form>
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top">
        <el-form-item :label="proxy.$t('login.username')" prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            :placeholder="proxy.$t('login.username')"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item :label="proxy.$t('login.password')" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            :placeholder="proxy.$t('login.password')"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="captchaEnabled" :label="proxy.$t('login.code')" prop="code">
          <!-- .fl-code-row / .fl-code 由组件样式提供，栅格与确认稿一致（输入框 + 93px 验证码位） -->
          <div class="fl-code-row">
            <el-input
              v-model="loginForm.code"
              size="large"
              auto-complete="off"
              :placeholder="proxy.$t('login.code')"
              @keyup.enter="handleLogin"
            >
              <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
            </el-input>
            <button type="button" class="fl-code" aria-label="刷新验证码" @click="getCode">
              <img v-if="codeUrl" :src="codeUrl" alt="验证码" />
            </button>
          </div>
        </el-form-item>

        <el-checkbox v-model="loginForm.rememberMe" class="login-remember">
          {{ proxy.$t('login.rememberPassword') }}
        </el-checkbox>

        <el-form-item class="login-submit">
          <el-button :loading="loading" size="large" type="primary" style="width: 100%" @click.prevent="handleLogin">
            <span v-if="!loading">{{ proxy.$t('login.login') }}</span>
            <span v-else>{{ proxy.$t('login.logging') }}</span>
          </el-button>
          <div v-if="register" style="float: right">
            <router-link class="link-type" :to="'/register'">{{ proxy.$t('login.switchRegisterPage') }}</router-link>
          </div>
        </el-form-item>
      </el-form>
    </template>

    <!-- 确认稿的页脚文案 + 原工程版权行（保留原有信息，可随时删除下面这一行） -->
    <template #footer>
      <div class="fl-poetry">
        每一个灵感，都值得盛放。<small>IMAGINATION IN BLOOM / 2026</small>
        <span class="login-copyright">Copyright © 2018-2026 疯狂的狮子Li All Rights Reserved.</span>
      </div>
    </template>
  </FloralLogin>
</template>

<script setup lang="ts">
import FloralLogin from '@/components/FloralLogin/FloralLogin.vue';
import { getCodeImg } from '@/api/login';
import { useUserStore } from '@/store/modules/user';
import { LoginData } from '@/api/types';
import { to } from 'await-to-js';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const userStore = useUserStore();
const router = useRouter();
const { t } = useI18n();

const loginForm = ref<LoginData>({
  username: 'admin',
  password: 'admin123',
  rememberMe: false,
  code: '',
  uuid: ''
} as LoginData);

const loginRules: ElFormRules = {
  username: [{ required: true, trigger: 'blur', message: t('login.rule.username.required') }],
  password: [{ required: true, trigger: 'blur', message: t('login.rule.password.required') }],
  code: [{ required: true, trigger: 'change', message: t('login.rule.code.required') }]
};

const codeUrl = ref('');
const loading = ref(false);
// 验证码开关
const captchaEnabled = ref(true);

// 注册开关
const register = ref(false);
const redirect = ref('/');
const loginRef = ref<ElFormInstance>();

watch(
  () => router.currentRoute.value,
  (newRoute: any) => {
    redirect.value = newRoute.query && newRoute.query.redirect && decodeURIComponent(newRoute.query.redirect);
  },
  { immediate: true }
);

const handleLogin = () => {
  loginRef.value?.validate(async (valid: boolean, fields: any) => {
    if (valid) {
      loading.value = true;
      // 勾选了需要记住密码设置在 localStorage 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        localStorage.setItem('username', String(loginForm.value.username));
        localStorage.setItem('password', String(loginForm.value.password));
        localStorage.setItem('rememberMe', String(loginForm.value.rememberMe));
      } else {
        // 否则移除
        localStorage.removeItem('username');
        localStorage.removeItem('password');
        localStorage.removeItem('rememberMe');
      }
      // 调用action的登录方法
      const [err] = await to(userStore.login(loginForm.value));
      if (!err) {
        const redirectUrl = redirect.value || '/';
        await router.push(redirectUrl);
        loading.value = false;
      } else {
        loading.value = false;
        // 重新获取验证码
        if (captchaEnabled.value) {
          await getCode();
        }
      }
    } else {
      console.log('error submit!', fields);
    }
  });
};

/**
 * 获取验证码
 */
const getCode = async () => {
  const res = await getCodeImg();
  const { data } = res;
  captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
  if (captchaEnabled.value) {
    // 刷新验证码时清空输入框
    loginForm.value.code = '';
    codeUrl.value = 'data:image/gif;base64,' + data.img;
    loginForm.value.uuid = data.uuid;
  }
};

const getLoginData = () => {
  const username = localStorage.getItem('username');
  const password = localStorage.getItem('password');
  const rememberMe = localStorage.getItem('rememberMe');
  loginForm.value = {
    username: username === null ? String(loginForm.value.username) : username,
    password: password === null ? String(loginForm.value.password) : String(password),
    rememberMe: rememberMe === null ? false : Boolean(rememberMe)
  } as LoginData;
};

onMounted(() => {
  getCode();
  getLoginData();
});
</script>

<style lang="scss" scoped>
/*
  为什么选择器写成 `.zx-floral-login :deep(...)` 而不是带上 .fl-panel：
  Vue 的作用域属性只会加到本组件模板渲染出的元素上（子组件仅根元素带父作用域）。
  .fl-panel 是 FloralLogin 内部元素，不带本页的 data-v，写 `.fl-panel[data-v] ...`
  会导致规则根本不匹配 —— 这就是先前输入行被撑到 150px 却没被这几条规则压住的原因。
  `.zx-floral-login` 是子组件根元素，会带上本页作用域属性，挂在这里才生效。

  规则说明：
  1) 标签字重：全局 base 有 `label{font-weight:600}`，vendors 里还有
     `.el-form .el-form-item__label{font-weight:1000}`，都比确认稿粗，压回 400。
  2) 输入行高度：Element Plus 的 `.el-input__prefix{height:100%}` 与
     `.el-input .el-input__icon{height:inherit}` 会让 <svg> 高度变成"未定"，
     替换元素没有内在尺寸时浏览器按默认 150px 处理 → 整行被撑到 150px、卡片溢出屏幕。
     这里既收回图标高度（1em，治根），也按确认稿把输入行定在 44px。
  3) 页脚版权行与"记住我"间距。
*/
.zx-floral-login :deep(.el-form-item__label) {
  font-weight: 400;
}

.zx-floral-login :deep(.el-input__icon) {
  height: 1em;
}

.zx-floral-login :deep(.el-input),
.zx-floral-login :deep(.el-select) {
  height: 44px;
}

.login-remember {
  display: block;
  margin: 0 0 20px;
}

.login-submit {
  margin-bottom: 0;
}

.login-copyright {
  display: block;
  margin-top: 4px;
  font-size: 9px;
  line-height: 1.5;
  letter-spacing: 0.5px;
  color: #8a95a4;
}
</style>
