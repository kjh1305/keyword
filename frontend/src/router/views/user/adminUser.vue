<template>
  <Layout>
    <PageHeader :title="title" :items="items"/>

    <div class="row">
      <div class="col-lg-12">
        <div class="card">
          <div class="card-body">
            <h4 class="card-title mb-4">사용자 필터</h4>
            <b-form class="outer-repeater" @submit.prevent="filterUser">
              <div data-repeater-list="outer-group" class="outer">
                <div data-repeater-item class="outer">
                  <!-- 검색 -->
                  <div class="form-group row mb-4">
                    <label for="taskname" class="col-form-label col-lg-2">검색어</label>
                    <div class="col-lg-10">
                      <input
                          id="taskname"
                          name="taskname"
                          v-model="userSearchKeyword"
                          type="text"
                          class="form-control"
                          placeholder="아이디 또는 이름을 입력해주세요...."
                      />
                    </div>
                  </div>
                  <!-- 가입 기간 -->
                  <div class="form-group row mb-4">
                    <label class="col-form-label col-lg-2">가입기간</label>
                    <div class="col-lg-10">
                      <date-picker v-model="userDateRange" range append-to-body lang="ko" confirm></date-picker>
                    </div>
                  </div>
                  <!--상태-->
                  <div class="form-group row mb-4">
                    <label class="col-md-2 col-form-label">상태</label>
                    <div class="col-md-10">
                      <b-form-select
                          class="form-select"
                          label-field="상태"
                          v-model="userSearchStatus"
                          size="lg"
                          :options="userSearchStatusOptions"
                      ></b-form-select>
                    </div>
                  </div>
                </div>
              </div>
              <div class="row justify-content-end">

                <div class="col-lg-10">
                  <!--필터 초기화-->
                  <b-button variant="outline-success" @click="refreshFilter">
                    <i class="mdi mdi-refresh label-icon"></i>
                  </b-button>
                  <b-button style="float:right;" class="button" variant="primary" type="submit">
                    조회
                  </b-button>
                </div>
              </div>
            </b-form>
          </div>
        </div>
      </div>
    </div>

    <!-- 사용자 리스트 -->
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="card-title">사용자 리스트</h4>
            <div class="row mt-4">
              <p>총 {{ ownerclanUserList.length }}건</p>
            </div>
            <!-- Table -->
            <div class="table-responsive mb-0">
              <b-table :items="ownerclanUserList" :fields="ownerclanUserFields" responsive="sm" :per-page="perPage" :current-page="currentPage">
                <template #cell(status)="data">
                  {{data.item.status==='ok'? '정상':'정지'}}
                </template>

                <template #cell(manage)="data"><!--data는 현재 행의 데이터 값-->
                  <div class="button-items">
                    <b-button variant="outline-success" class="xs" v-b-modal.modal-scrollable-update-user @click="initUpdateUser(data)">
                      <i class="mdi mdi-pencil label-icon"></i>
                    </b-button>
                    <b-button variant="outline-danger" class="xs" @click="deleteCheck(data)" >
                      <i class="mdi mdi-trash-can label-icon"></i>
                    </b-button>
                  </div>
                </template>

              </b-table>
            </div>

            <b-button style="float:right;" variant="primary" v-b-modal.modal-scrollable-create-user>
              등록
            </b-button>

            <div class="row">
              <div class="col">
                <div class="dataTables_paginate paging_simple_numbers float-right">
                  <ul class="pagination pagination-rounded mb-0">
                    <!-- pagination -->
                    <b-pagination align="center" v-model="currentPage" :total-rows="rows" :per-page="perPage"></b-pagination>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- modal -->
    <!-- update user -->
    <b-modal hide-footer
             id="modal-scrollable-update-user"
             ref="updateUserModal" scrollable title="사용자 수정">
      <b-form class="p-2" @submit.prevent="updateUser">
        <div data-repeater-list="outer-group" class="outer">
          <div data-repeater-item class="outer">
            <!-- 검색 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">아이디</label>
              <div class="col-lg-10">
                <b-form-input
                    v-model="userForm.userId"
                    placeholder="아이디를 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.userId.$error}"
                    disabled></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.userId.required"
                    class="error">
                  아이디를 입력해주세요
                </div>
              </div>
            </div>

            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">이름</label>
              <div class="col-lg-10">
                <b-form-input
                    id="input-2"
                    v-model="userForm.username"
                    placeholder="이름을 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.username.$error}"></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.username.required"
                    class="error">
                  이름을 입력해주세요
                </div>
              </div>
            </div>

            <!-- 비밀번호 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">비밀번호</label>
              <div class="col-lg-10">
                <b-form-input
                    v-model="userForm.password"
                    type="password"
                    placeholder="비밀번호를 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.password.$error}"></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.password.required"
                    class="error">
                  비밀번호를 입력해주세요
                </div>
              </div>
            </div>

            <!-- 비밀번호 확인 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">비밀번호 확인</label>
              <div class="col-lg-10">
                <b-form-input
                    v-model="userForm.confirmPassword"
                    type="password"
                    placeholder="비밀번호를 입력해주세요"
                    :class="{'is-invalid' : submitted && $v.userForm.confirmPassword.$error}"
                ></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.confirmPassword.sameAsPassword"
                    class="error">
                  비밀번호가 다릅니다
                </div>
              </div>
            </div>

            <!-- 상태 -->
            <div class="form-group row mb-4">
              <label class="col-md-2 col-form-label">상태</label>
              <div class="col-md-10">
                <b-form-select
                    class="form-select"
                    label-field="상태"
                    v-model="userForm.status"
                    size="lg"
                    :options="statusOptions"
                ></b-form-select>
              </div>
            </div>
          </div>
        </div>
        <div class="row justify-content-end">
          <div class="col-lg-10">
            <b-button style="float:right;" class="button" variant="primary" type="submit">
              등록
            </b-button>
          </div>
        </div>
      </b-form>
    </b-modal>

    <!-- create user form modal-->
    <b-modal hide-footer
             id="modal-scrollable-create-user"
             ref="createUserModal" scrollable title="사용자 등록">
      <b-form class="p-2" @submit.prevent="createUser">
        <div data-repeater-list="outer-group" class="outer">
          <div data-repeater-item class="outer">
            <!-- 아이디 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">아이디</label>
              <div class="col-lg-10">
                <b-form-input
                    id="input-1"
                    v-model="userForm.userId"
                    placeholder="아이디를 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.userId.$error}"></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.userId.required"
                    class="error">
                  아이디를 입력해주세요
                </div>
                <div
                    v-if="submitted && !$v.userForm.userId.alphaNum"
                    class="error">
                  아이디는 영문자와 숫자만 입력해주세요
                </div>
              </div>
            </div>

            <!-- 이름 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">이름</label>
              <div class="col-lg-10">
                <b-form-input
                    id="input-2"
                    v-model="userForm.username"
                    placeholder="이름을 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.username.$error}"></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.username.required"
                    class="error">
                  이름을 입력해주세요
                </div>
              </div>
            </div>

            <!-- 비밀번호 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">비밀번호</label>
              <div class="col-lg-10">
                <b-form-input
                    v-model="userForm.password"
                    type="password"
                    placeholder="비밀번호를 입력하세요"
                    :class="{'is-invalid' : submitted && $v.userForm.password.$error}"></b-form-input>
                <div
                    v-if="submitted && !$v.userForm.password.required"
                    class="error">
                  비밀번호를 입력해주세요
                </div>
              </div>
            </div>

            <!-- 비밀번호 확인 입력 -->
            <div class="form-group row mb-4">
              <label class="col-form-label col-lg-2">비밀번호 확인</label>
              <div class="col-lg-10">
                <b-form-input
                    v-model="userForm.confirmPassword"
                    type="password"
                    placeholder="비밀번호를 입력해주세요"
                    :class="{'is-invalid' : submitted && $v.userForm.confirmPassword.$error}"
                ></b-form-input>

                <div
                    v-if="submitted && !$v.userForm.confirmPassword.sameAsPassword"
                    class="error">
                  비밀번호가 다릅니다
                </div>

              </div>
            </div>

            <!--상태-->
            <div class="form-group row mb-4">
              <label class="col-md-2 col-form-label">상태</label>
              <div class="col-md-10">
                <b-form-select
                    class="form-select"
                    label-field="상태"
                    v-model="userForm.status"
                    size="lg"
                    :options="statusOptions"
                ></b-form-select>
              </div>
            </div>

          </div>
        </div>
        <div class="row justify-content-end">
          <div class="col-lg-10">
            <b-button style="float:right;" class="button" variant="primary" type="submit">
              등록
            </b-button>
          </div>
        </div>
      </b-form>
    </b-modal>
  </Layout>
</template>


<script>
import Layout from "../../layouts/main";
import appConfig from "@/app.config";
import PageHeader from "@/components/page-header";
import DatePicker from 'vue2-datepicker'

import {ownerclanUserService} from "@/helpers/userservice/ownerclan-user.service";
// eslint-disable-next-line no-unused-vars
import {required, sameAs,alphaNum} from "vuelidate/lib/validators";

export default {
  page: {
    title: "사용자 관리",
    meta: [
      {
        name: "description",
        content: appConfig.description,
      },
    ],
  },
  components: {
    DatePicker,
    Layout,
    PageHeader,
  },
  validations :{//사용자 validation
    userForm : {
      password : {
        required
      },
      userId : {
        required,
        alphaNum
      },
      username : {
        required
      },
      confirmPassword :{//비밀번호 확인 절차 validation
        sameAsPassword: sameAs('password')
      }
    }
  },
  data() {
    return {
      title: "사용자 관리",
      items: [
        {
          text: "메인",
          href: "/",
        },
        {
          text: "사용자 관리",
          href: "/user/admin"
        }
      ],
      //사용자 검색 필터
      userSearchKeyword : "",//검색 키워드 필터(아이디 ,이름)
      userDateRange: '',
      userSearchStatus : "all",// 상태 필터
      userSearchStatusOptions:[
        {text: '전체', value: 'all'},
        {text: '정상', value: 'ok'},
        {text: '정지', value: 'stop'}
      ],
      selectedDateOption : 1,//선택 date 옵션
      statusOptions: [//상태 옵션
        {text: '정상', value: 'ok'},
        {text: '정지', value: 'stop'}
      ],

      //사용자 리스트
      submitted : false,
      ownerclanUserList: [],
      ownerclanUserFields :[
        {
          key : "no",
          label : "번호",
          sortable: true
        },
        {
          key : "userId",
          label : "아이디",
          sortable: true
        },
        {
          key : "username",
          label : "이름",
          sortable: true
        },
        {
          key : 'status',
          label : '상태',
          sortable: true
        },
        {
          key: 'createdAt',
          label : '등록일시',
          sortable: true
        },
        {
          key: 'updatedAt',
          label : '수정일시',
          sortable: true
        },
        {
          key: 'lastLoginDate',
          label : '최종로그인 일시',
          sortable: true
        },
        {
          key: 'ipAddress',
          label : '로그인 아이피',
          sortable: true
        },
        {
          key: 'manage',
          label : '관리'
        },
      ],
      currentPage: 1,
      perPage: 10,//perpage가 한 페이지에 몇개 보여줄지를 선택하는 옵션

      //사용자 등록,수정
      userForm: { //사용자 등록 form
        userId: '',
        password: '',
        confirmPassword: '',
        username: '',
        status: 'ok',
      }
    }
  },


  created() {
    console.log("admin user page loaded")
    this.$root.$on('bv::modal::hidden', this.initForm)//modal 종료시에 값 처리
    this.getAllUser()//페이지 로드시 전체 사용자 조회
  },
  computed: {
    rows() { //리스트의 갯수 리턴
      return this.ownerclanUserList.length
    },
  },
  methods: {

    getAllUser() {//전체 사용자 조회(vue app 생성시에 call)
      ownerclanUserService.getAllOwnerclanUser().then(data => {
        const objArr = data.map((item,index)=>{
          return {
            no : index+1,
            userId : item.userId,
            password : item.password,
            username : item.username,
            status : item.status,
            lastLoginDate : item.lastLoginDate,
            ipAddress : item.ipAddress,
            createdAt : item.createdAt,
            updatedAt : item.updatedAt,
            manage : ""
          }
        })
        this.ownerclanUserList = objArr
      })
    },
    refreshFilter(){
      this.userDateRange ="";
      this.userSearchStatus = "all";
      this.userSearchKeyword = "";
    },
    filterUser(){//조건에 따른 사용자 조회(날짜, 검색어 ,상태)
      const startDate = new Date(this.userDateRange[0]);
      const endDate = new Date(this.userDateRange[1]);

      const word = encodeURIComponent(this.userSearchKeyword);//빈칸 없애기

      //NOTE 날짜 형식 yyyy.mm.dd
      ownerclanUserService.getFilteredUser(startDate.toLocaleDateString().replace(/ /gi, ''),
          word,this.userSearchStatus,endDate.toLocaleDateString().replace(/ /gi, '')).then(data =>{
        this.ownerclanUserList = data.map((item, index) => {
          return {
            no: index + 1,
            userId: item.userId,
            password: item.password,
            username: item.username,
            status: item.status,
            ipAddress : item.ipAddress,
            lastLoginDate: item.lastLoginDate,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
            manage: ""
          }
        })
      })
    },
    initForm(){//modal close시에 처리 함수
      this.submitted = false;
      this.userForm = { //사용자 등록 용 폼 형식
        userId: '',
        password: '',
        confirmPassword: '',
        username: '',
        status: 'ok',
      }
    },
    createUser() {//사용자 등록
      this.submitted = true;//submit 후에 validation 작동

      console.log(this.userForm)

      this.$v.$touch()
      if(!this.$v.$invalid){//입력 에러가 있는 경우
        ownerclanUserService.addUser(this.userForm).then(data => {
          if (data === 1) {
            alert(`사용자 등록 성공`)
            this.$router.go()//성공시 페이지 새로고침
          }else{
            alert(`사용자 등록 실패(이미 존재하는 아이디)`)
          }
        })
      }else{
        alert(`사용자 등록 실패(사용자 정보를 정확히 입력해주세요)`)
      }
    },
    initUpdateUser(data){
      ownerclanUserService.getUserByUserId(data.item.userId).then(result =>{
        this.userForm = {
            userId: result.userId,
            username: result.username,
            status: result.status,
            password: "",
            confirmPassword: "",
          }
        })
    },
    updateUser(){//사용자 정보 수정(Key는 사용자아이디)
      this.submitted =true;

      this.$v.$touch()
      if(!this.$v.$invalid) {//입력 에러가 있는 경우
        ownerclanUserService.updateUser(this.userForm).then(data => {
          if(data === 1){
            alert(`사용자 수정 성공`)
            this.$router.go()//성공시 페이지 새로고침
          }else{
            alert(`사용자 수정 실패`)
          }
        })
      }else {
        alert(`사용자 수정 실패(사용자 정보를 정확히 입력해주세요)`)
      }
    },
    deleteCheck(data){
      if(confirm("삭제하시겠습니까?")){
        this.deleteUser(data);
      }
    },
    deleteUser(data){//사용자 삭제
      ownerclanUserService.deleteUserByUserId(data.item.userId).then(data=>{
        if(data===1){
          alert(`사용자 삭제 성공`)
          this.$router.go()
        }else{
          alert(data)
        }
      })
    }
  }
}
</script>
<style>
/* custom container style */
.custom--container {
  margin: 20px;
  border: solid 1px lightgray;
  padding: 2em;
}

</style>
