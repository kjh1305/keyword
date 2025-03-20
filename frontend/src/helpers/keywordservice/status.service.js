export const statusService = {
    getStatus,
    killStatus
}

//status 조회
function getStatus(id) {
    const requestOptions = {
        method: 'GET',
        headers: {

        },
    };
    return fetch(`/api/status/${id}`,requestOptions)
        .then(handleResponse)
        .then(status =>{
            return status;
        })

}

//status 강제종료
function killStatus(id){
    const requestOptions = {
        method: 'PUT',
    };

    return fetch(`/api/status/kill/${id}`,requestOptions)
        .then(handleResponse)
        .then(() =>{
            alert("중단 완료")
        })
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
