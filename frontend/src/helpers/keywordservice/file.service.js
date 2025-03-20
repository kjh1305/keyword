import axios from "axios";

export const fileService = {
    uploadExcelFile,
    getAllWorkList,
    downloadExcelFile,
    changeStatusCode
}

//작업 리스트 가져오기
function getAllWorkList(){
    const requestOptions = {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        },
    };

    return fetch(`/api/work/`, requestOptions)
        .then(handleResponse)
        .then(data => {
            return data;
        });
}

//엑셀 업로드
function uploadExcelFile(excelFile,useKipris,searchCount,sellerCountMin,sellerCountMax){

    const formData = new FormData();
    formData.append("file",excelFile);
    formData.append("useKipris",useKipris)
    formData.append("sellerMin",sellerCountMin)
    formData.append("sellerMax",sellerCountMax)
    formData.append("searchCount",searchCount)

    const requestOptions = {
        method: 'POST',
        headers: {
        },
        body : formData
    };

    return fetch(`/api/keyword/excel`, requestOptions)
        .then(handleResponse)
        .then(data => {
            return data;
        });
}

function downloadExcelFile(filename){
    axios({
        url: '/api/keyword/file?filename=' + filename,
        method: 'GET',
        responseType: 'blob',
    }).then((response) => {
        console.log(response);

        let fileURL = window.URL.createObjectURL(new Blob([response.data]));
        let fileLink = document.createElement('a');

        fileLink.href = fileURL;
        fileLink.setAttribute('download', filename+'.xlsx');
        document.body.appendChild(fileLink);

        fileLink.click();
    });
}

function changeStatusCode(id){

    const requestOptions = {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
    };

    return fetch(`/api/work/statuscode/${id}`, requestOptions)
        .then(handleResponse)
        .then(data => {
            return data;
        });

}

function handleResponse(response) {
    return response.text().then(text => {
        const data = text && JSON.parse(text);
        if (!response.ok) {
            if (response.status === 401) {
                console.log(`error code : 401`)
            }
            const error = (data && data.message) || response.statusText;
            return Promise.reject(error);
        }
        return data;
    });
}