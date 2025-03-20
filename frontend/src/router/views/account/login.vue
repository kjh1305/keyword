<script>

import Layout from "../../layouts/auth";
import {
  authMethods,
  authFackMethods,
  notificationMethods,
} from "@/state/helpers";
import {mapState} from "vuex";

import appConfig from "@/app.config";
//import {required, email} from "vuelidate/lib/validators";
import {required} from "vuelidate/lib/validators";

/**
 * Login component
 */
export default {
  page: {
    title: "Login",
    meta: [
      {
        name: "description",
        content: appConfig.description,
      },
    ],
  },
  components: {
    Layout,
  },
  data() {
    return {
      email: "admin",
      password: "",
      submitted: false,
      authError: null,
      tryingToLogIn: false,
      isAuthError: false,
    };
  },
  validations: {//아이디 및 비밀번호 유효성 검사
    email: {
      required,
    },
    password: {
      required,
    },
  },
  computed: {
    ...mapState("authfack", ["status"]),
    notification() {
      return this.$store ? this.$store.state.notification : null;
    },
  },
  methods: {
    ...authMethods,
    ...authFackMethods,
    ...notificationMethods,

    //로그인 메소드
    tryToLogIn() {
      this.submitted = true;
      // stop here if form is invalid
      this.$v.$touch();

      if (this.$v.$invalid) {
        return;
      } else {
        const {email, password} = this;
        if (email && password) {
          this.login({
            email,
            password,
          });
        }
      }
    },
  },
  mounted() {
  },
};
</script>

<template>
  <Layout>
    <div class="row justify-content-center">
      <div class="col-md-8 col-lg-6 col-xl-5">
        <div class="card overflow-hidden">
          <div class="card-body pt-0 mt-5">
            <div class="text-primary p-4" style="text-align: center">
              <h2 class="text-secondary">쿠팡위너 추출기 관리자</h2>
            </div>
            <b-alert
                v-model="isAuthError"
                variant="danger"
                class="mt-3"
                dismissible
            >{{ authError }}
            </b-alert>
            <div
                v-if="notification.message"
                :class="'alert ' + notification.type">
<!--              {{ notification.message }}--> 다시 로그인 시도하여 주세요
            </div>

            <b-form class="p-2" @submit.prevent="tryToLogIn">
              <!-- 이메일 입력 -->
              <b-form-group
                  class="mb-3"
                  id="input-group-1"
                  label="아이디"
                  label-for="input-1"
              >
                <b-form-input
                    id="input-1"
                    v-model="email"
                    type="text"
                    placeholder="아이디를 입력해주세요"
                    :class="{ 'is-invalid': submitted && $v.email.$error }"
                ></b-form-input>
                <div
                    v-if="submitted && $v.email.$error"
                    class="invalid-feedback"
                >
                  <span v-if="!$v.email.required">Id is required.</span>
                </div>
              </b-form-group>
              <!-- 비밀번호 입력 -->
              <b-form-group
                  class="mb-3"
                  id="input-group-2"
                  label="비밀번호"
                  label-for="input-2"
              >
                <b-form-input
                    id="input-2"
                    v-model="password"
                    type="password"
                    placeholder="비밀번호를 입력해주세요"
                    :class="{ 'is-invalid': submitted && $v.password.$error }"
                ></b-form-input>
                <div
                    v-if="submitted && !$v.password.required"
                    class="invalid-feedback"
                >
                  Password is required.
                </div>
              </b-form-group>
<!--              <b-form-checkbox
                  class="form-check"
                  id="customControlInline"
                  name="checkbox-1"
                  value="accepted"
                  unchecked-value="not_accepted">
                아이디 저장
              </b-form-checkbox>-->
              <div class="mt-3 d-grid">
                <b-button type="submit" variant="primary" class="btn-block"
                >로그인
                </b-button
                >
              </div>
              <div class="mt-4 text-center">

              </div>
            </b-form>
          </div>
          <!-- end card-body -->
        </div>
        <!-- end card -->

        <!-- end row -->
      </div>
      <!-- end col -->
    </div>
    <!-- end row -->
  </Layout>
</template>

<style lang="scss" module></style>
