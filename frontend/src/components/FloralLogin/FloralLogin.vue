<template>
  <div ref="root" class="zx-floral-login">
    <img v-if="fallback" class="fl-fallback" :src="fallbackImage" alt="" aria-hidden="true" />
    <canvas ref="canvas" class="fl-canvas" aria-hidden="true"></canvas>
    <header class="fl-header">
      <div class="fl-brand"><span></span>AI CREATIVE PLATFORM</div>
      <slot name="language" :english="english" :toggle="toggleLanguage">
        <button type="button" class="fl-language" aria-label="切换语言" @click="toggleLanguage">
          {{ english ? '中文 · English' : '简体中文 · EN' }}
        </button>
      </slot>
    </header>
    <main class="fl-stage">
      <section class="fl-panel" aria-label="登录表单">
        <h2>{{ english ? 'AI Creative Platform' : '纵享集团AI创作平台' }}</h2>
        <p class="fl-subtitle">{{ english ? 'Let your ideas bloom' : '让灵感，自然绽放' }}</p>
        <!-- Recommended: pass the project's existing el-form here, preserving its ref, rules and handlers. -->
        <slot name="form" :english="english">
          <form @submit.prevent="submit">
            <slot name="before-fields" :english="english"></slot>
            <label class="fl-field">
              <span class="fl-label">{{ english ? 'Account' : '账号' }}</span>
              <input class="fl-input" name="username" autocomplete="username" :value="modelValue.username"
                :placeholder="english ? 'Your account' : '请输入账号'" :disabled="loading" required
                @input="updateField('username', $event.target.value)" />
            </label>
            <label class="fl-field">
              <span class="fl-label">{{ english ? 'Password' : '密码' }}</span>
              <input class="fl-input" type="password" name="password" autocomplete="current-password" :value="modelValue.password"
                :placeholder="english ? 'Your password' : '请输入密码'" :disabled="loading" required
                @input="updateField('password', $event.target.value)" />
            </label>
            <div v-if="captchaEnabled" class="fl-field">
              <span class="fl-label">{{ english ? 'Verification' : '验证码' }}</span>
              <div class="fl-code-row">
                <input class="fl-input" name="code" autocomplete="off" :value="modelValue.code"
                  :aria-label="english ? 'Verification code' : '验证码'" :placeholder="english ? 'Enter code' : '请输入验证码'"
                  :disabled="loading" required @input="updateField('code', $event.target.value)" />
                <button class="fl-code" type="button" :disabled="loading || captchaLoading"
                  :aria-label="english ? 'Refresh verification code' : '刷新验证码'" @click="$emit('refresh-captcha')">
                  <img v-if="captchaUrl && !captchaLoading" :src="captchaUrl" alt="验证码" />
                  <span v-else>{{ english ? 'Refresh' : '点击刷新' }}</span>
                </button>
              </div>
            </div>
            <slot name="after-fields" :english="english"></slot>
            <label class="fl-remember">
              <input type="checkbox" :checked="modelValue.rememberMe" :disabled="loading"
                @change="updateField('rememberMe', $event.target.checked)" />
              <span>{{ english ? 'Remember me' : '记住我' }}</span>
            </label>
            <p v-if="error" class="fl-error" role="alert">{{ error }}</p>
            <button class="fl-submit" type="submit" :disabled="!ready || loading || (captchaEnabled && (!captchaUrl || captchaLoading))">
              {{ loading ? (english ? 'Signing in…' : '登录中…') : (english ? 'Sign in' : '登录') }}
            </button>
          </form>
        </slot>
        <slot name="extra-auth" :english="english"></slot>
        <p class="fl-note">灵感无限 · 创作无界</p>
      </section>
    </main>
    <footer class="fl-footer">
      <slot name="footer"><div class="fl-poetry">每一个灵感，都值得盛放。<small>IMAGINATION IN BLOOM / 2026</small></div></slot>
      <button v-if="!fallback" class="fl-motion" type="button" :aria-pressed="playing" @click="toggleMotion">
        <i></i><span>{{ playing ? (english ? 'Pause motion' : '暂停动态') : (english ? 'Play motion' : '播放动态') }}</span>
      </button>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
import { createFloralScene } from './floral-scene.js';
import fallbackImage from './flower-fallback.png';
import './floral-login.css';

const props = defineProps({
  modelValue: { type: Object, default: () => ({ username: '', password: '', code: '', rememberMe: false }) },
  captchaEnabled: { type: Boolean, default: true },
  captchaUrl: { type: String, default: '' },
  captchaLoading: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  // Enable only after wiring the real login handler. Does not affect an existing form passed via slot.
  ready: { type: Boolean, default: false },
  error: { type: String, default: '' }
});
const emit = defineEmits(['update:modelValue', 'submit', 'refresh-captcha', 'language-change']);
const root = ref(null);
const canvas = ref(null);
const english = ref(false);
const fallback = ref(false);
const playing = ref(true);
let scene = null;
let media = null;

function updateField(key, value) {
  // Preserve tenantId, uuid, clientId and any other project fields in the parent object.
  emit('update:modelValue', { ...props.modelValue, [key]: value });
}
function submit() {
  if (!props.ready || props.loading || (props.captchaEnabled && (!props.captchaUrl || props.captchaLoading))) return;
  emit('submit');
}
function toggleLanguage() {
  english.value = !english.value;
  emit('language-change', english.value ? 'en' : 'zh-CN');
}
function toggleMotion() {
  playing.value = !playing.value;
  scene?.setPlaying(playing.value);
}
function preferenceChanged(event) {
  playing.value = !event.matches;
  scene?.setPlaying(playing.value);
}
onMounted(() => {
  media = window.matchMedia('(prefers-reduced-motion: reduce)');
  playing.value = !media.matches;
  media.addEventListener?.('change', preferenceChanged);
  scene = createFloralScene(root.value, canvas.value, {
    playing: playing.value,
    onFallback: () => { fallback.value = true; }
  });
});
onDeactivated(() => scene?.setPlaying(false));
onActivated(() => scene?.setPlaying(playing.value));
onBeforeUnmount(() => {
  media?.removeEventListener?.('change', preferenceChanged);
  scene?.destroy();
  scene = null;
});
</script>
