<template>
  <Layout>
    <PageHeader :title="title" :items="items"/>


    <div class="row justify-content-center">
      <div class="col-md-8 col-lg-6 col-xl-8">

        <!--파일 업로드-->
        <div class="col-12 ">
          <div class="card ">
            <div class="card-body mb-3">
              <div class="mb-3">
                <h4 class="card-title mb-3">파일 업로드</h4>

                <div class="row" style="margin-bottom: 15px">
                  <!--파일선택-->
                  <div class="col-lg-4">
                    <b-form-group label="파일 선택" label-for="input_select_file">
                      <b-form-file
                          id="input_select_file"
                          v-model="selectedFiles"
                          accept=".xls, .xlsx, .xlsm, .csv"
                          :state="Boolean(selectedFiles)"
                          plain
                      ></b-form-file>
                    </b-form-group>
                  </div>
                </div>

                <div class="row" style="margin-bottom: 15px">
                  <div class="col-lg-4">
                    <b-form-group label="검색량" label-for="input_search_count">
                      <b-form-input
                          id="input_search_count" size="sm" v-model="searchCount"
                          placeholder="검색량" type="number" min="0"></b-form-input>
                    </b-form-group>
                  </div>

                  <div class="col-lg-4">
                    <b-form-group label="최소 판매자수" label-for="input_seller_count_min">
                      <b-form-input
                          id="input_seller_count_min" size="sm" v-model="sellerCountMin"
                          placeholder="최소 판매자수" type="number" min="0"></b-form-input>
                    </b-form-group>
                  </div>
                  <div class="col-lg-4">
                    <b-form-group label="최대 판매자수" label-for="input_seller_count_max">
                      <b-form-input
                          id="input_seller_count_max" size="sm" v-model="sellerCountMax"
                          placeholder="최대 판매자수" type="number" min="0"></b-form-input>
                    </b-form-group>
                  </div>
                </div>

                <div class="col-lg-4">
                  <b-form-checkbox
                      v-model="useKipris"
                      value="사용"
                      unchecked-value="미사용"
                  ><label style="margin-left: 10px">특허 체크</label></b-form-checkbox>
                </div>

                <!--실행 버튼-->
                <b-button variant="success"
                          :disabled="selectedFiles === null"
                          @click="uploadExcelFile">
                  실행
                </b-button>

              </div>
            </div>
          </div>
        </div>


        <!-- progress -->
        <div class="col-12">
          <div class="card">
            <div class="card-body mb-3">
              <h4 class="card-title mb-3">진행도</h4>
              <div class="row mb-2">
                <label class="col-lg-2">추출 작업</label>
                <div class="col-lg-10">

                  <b-progress  :max="extractTotal" height="20px" class="mb-3" animated>
                    <b-progress-bar :value="extractProgress"
                                    :label="`${((extractProgress / extractTotal) * 100).toFixed(0)}%`"
                                     >
                    </b-progress-bar>
                  </b-progress>
                </div>
              </div>

              <div class="row mb-2">
                <label class="col-lg-2">엑셀 가공</label>
                <div class="col-lg-10">
                  <!-- progress -->
                  <b-progress :max="excelTotal" height="20px" class="mb-3" animated>
                    <b-progress-bar :value="excelProgress"
                                    :label="`${((excelProgress / excelTotal) * 100).toFixed(0)}%`"
                                    variant="success">
                    </b-progress-bar>
                  </b-progress>
                </div>
              </div>

              <div class="row mb-2">
                <label class="col-lg-2">파일명</label>
                <div class="col-lg-10"><p>{{ this.currentFilename }}</p></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 파일 히스토리 -->
        <div class="card overflow-hidden">
          <div class="card-body pt-0 mt-5">
            <h4 class="card-title">파일 목록</h4>

            <div class="table-responsive mb-0">
              <p>총 {{ this.rows }} 건</p>
              <b-table :items="fileList" :fields="fileListFields" responsive="sm"
                       :per-page="perPage" :current-page="currentPage">
                <template #cell(buttons)="data">
                  <div class="button-items">
                    <b-button variant="primary"
                              :disabled="data.item.downloadName===''
                                ||data.item.statusCode==='실패'"
                              @click="downloadExcelFile(data.item.downloadName)">
                      다운로드
                    </b-button>

                    <b-button v-if="data.item.statusCode==='진행중'
                        ||data.item.statusCode==='대기중'" variant="danger"
                              @click="stopWork(data.item.id)">
                      <i class="bx bx-block align-middle mr-2"></i>
                    </b-button>
                  </div>
                </template>
              </b-table>
            </div>
            <div class="row">
              <div class="col">
                <div class="dataTables_paginate paging_simple_numbers float-right">
                  <ul class="pagination pagination-rounded mb-0">
                    <!-- pagination -->
                    <b-pagination align="center" v-model="currentPage" :total-rows="rows"
                                  :per-page="perPage"></b-pagination>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Layout>
</template>

<script>
import Layout from "../../layouts/main";
import appConfig from "@/app.config";
import PageHeader from "@/components/page-header";
import {fileService} from "../../../helpers/keywordservice/file.service";
import {statusService} from "../../../helpers/keywordservice/status.service";

export default {
  page: {
    title: "유효키워드 추출기",
    meta: [
      {
        name: "description",
        content: appConfig.description,
      },
    ],
  },
  components: {
    Layout,
    PageHeader,
  },
  created() {
    console.log("유효키워드 추출기 created")

    //progress bar 및 리스트 동기화
    this.statusCnt = 0;
    this.syncWork();
  },
  computed: {
    rows() { //리스트의 갯수 리턴
      return this.fileList.length
    },
  },
  data() {
    return {
      title: "유효키워드 추출기",
      items: [
        {
          text: "메인",
          href: "/",
        },
        {
          text: "유효키워드 추출기",
          href: "/user/validKeyword"
        }
      ],
      selectedFiles: null,//업로드할 엑셀 파일

      /////test
      useKipris : "미사용",//키프리스 사용 여부 체크(테스트용)


      //progress bar
      statusId: "",//status id
      extractProgress: "",//추출 진행도
      extractTotal: "",//추출 최대값
      excelProgress: "",//엑셀 가공 진행도
      excelTotal: "",//엑셀 가공 최대값
      currentFilename: "",//현재 추출중인 파일명
      currentStatusCode: "",//현재 추출중인 상태코드
      loading: null,//setInterval 인스턴스
      statusCnt: 0,//setInterval 인스턴스
      checkLoading: null,

      //파일 업로드 조건
      searchCount: 1000,
      sellerCountMin: 0,
      sellerCountMax: 5000,

      //파일 리스트
      workList: [],//work 객체의 모든 정보를 담을 리스트
      fileList: [],//테이블에 표시할 work 리스트
      fileListFields: [ //파일히스토리 테이블 필드
        {
          key: "filename",
          label: "파일이름",
        },
        {
          key: "startTime",
          label: "시작시간",
        },
        {
          key: "endTime",
          label: "종료시간",
        },
        {
          key: "statusCode",
          label: "상태",
        },
        {
          key: "buttons",
          label: ""
        },
      ],
      currentPage: 1,//현재 페이지 번호
      perPage: 8,//perpage가 한 페이지에 몇개 보여줄지를 선택하는 옵션

    }
  },
  methods: {

    //서버로 엑셀파일 전송
    uploadExcelFile() {
      console.log("upload()")

      fileService.uploadExcelFile(this.selectedFiles,this.useKipris,this.searchCount,this.sellerCountMin,this.sellerCountMax).then(() => {
        console.log("upload called")
        this.statusCnt = 0;
        this.syncWork()
      })
    },
    //서버에서 완료된 엑셀파일 다운로드 요청
    downloadExcelFile(filename) {
      console.log("download()")
      fileService.downloadExcelFile(filename)
    },
    //progress bar 및 리스트 동기화
    //created , 업로드 후 , clearInterval 후에 호출
    syncWork() {
      clearInterval(this.checkLoading)
      fileService.getAllWorkList().then(data => {
        this.workList = data;

        this.fileList = data.map(obj => {
          return {
            id : obj.id,
            statusCode: this.statusConvert(obj.statusCode),
            filename: obj.filename,
            startTime: obj.startTime,
            endTime: obj.endTime,
            downloadName: obj.downloadName
          }
        })
        //현재 작업중인 progress bar가 없을때만 동기화 진행
        if (this.loading === null) {

          if (this.workList.filter((el) => el.statusCode === 2).length > 0) {
            this.statusId = this.workList.filter((el) => el.statusCode === 2)[0].id;
            this.loading = setInterval(this.getStatus, 10000);
          } else if (this.workList.filter((el) => el.statusCode === 2).length === 0 && this.workList.filter((el) => el.statusCode === 0).length > 0) {
            this.statusCnt++;
            if (this.statusCnt <= 20) {
              this.checkLoading = setInterval(() => this.syncWork(),2000)
            } else {
              this.statusCnt = 0; //초기화
              const id = Math.min(...this.workList.filter((el) => el.statusCode === 0).map((el) => el.id)); //첫번째 id
              fileService.changeStatusCode(id).then(()=>{
                console.log("지연 데이터 삭제")
                this.syncWork()
              })

            }
          }
        }
      })

    },
    statusConvert(code) {//statusCode 문자열로 변환
      if (code === 2) {
        return "진행중"
      } else if (code === 1) {
        return "성공"
      } else if (code === 0) {
        return "대기중"
      } else if (code === -1){
        return "특허 기능 사용 초과"
      } else if (code === 3){
        return "실패"
      }else if(code === -9) {
        return "강제종료"
      }else {
        return "상태이상"
      }

    },
    //download 진행도 조회 함수(polling)
    getStatus() {
      statusService.getStatus(this.statusId).then(status => {
        this.currentStatusCode = status.statusCode;

        if (status !== "") {//조회 결과 없는 경우는 무시
          this.excelProgress = status.excelProgress;
          this.excelTotal = status.excelTotal;
          this.extractProgress = status.filteringProgress;
          this.extractTotal = status.filteringTotal;
          this.currentFilename = status.filename;
        } else {
          console.log("에러발생 재시도")
          this.clearWork();
        }

        //파일다운로드가 끝난 경우 폴링 중지
        if (this.currentStatusCode === 1||this.currentStatusCode===3|| this.currentStatusCode === -9 || this.currentStatusCode === -1) {
          this.clearWork()
        }

      })
    },
    clearWork() {//폴링 함수 초기화 및 재실행
      this.currentStatusCode = "";
      this.excelProgress = "";
      this.excelTotal = "";
      this.extractProgress = "";
      this.extractTotal = "";
      this.currentFilename = "";

      clearInterval(this.loading)
      this.loading = null
      this.statusCnt = 0;
      this.syncWork()
    },
    stopWork(id){//작업 중단
      console.log("작업 중단!!!!")
      statusService.killStatus(id).then(() =>{
        this.syncWork()
      })
    }
  }
}
</script>