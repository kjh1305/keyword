import { authHeader } from './auth-header';

export const loginService = {
    login,
    logout,
    register,
    getAll,
    checkToken,
    getNewToken
};

//클라이언트 정보
const clientAuth = {
    username : "coupang_admin",
    password : "Api**OwnerClan**2021**"
}

//access_token 만료 되었을때 새 토큰 요청(refresh_token)
function getNewToken(){
    const refresh_token = JSON.parse(localStorage.getItem('user'))['refresh_token']
    const grant_type = "refresh_token"

    const formData = new FormData();
    formData.append('grant_type',grant_type);
    formData.append('refresh_token',refresh_token);

    const requestOptions = {
        method: 'POST',
        headers: {
            Authorization: 'Basic '+ btoa(clientAuth.username+ ":" +clientAuth.password)
        },
        body : formData
    };

    return fetch('/oauth/token',requestOptions)
        .then(handleResponse)
        .then(result => {
            console.log("get new token success")
            return result;
        }).catch(error =>{
            console.log("get new token error : " + error)
            return error;
        });
}

//페이지 이동시 access_token check
function checkToken(){

    const token = JSON.parse(localStorage.getItem('user'))['access_token']

    const formData = new FormData();
    formData.append('token',token)

    const requestOptions = {
        method: 'POST',
        headers: {
            Authorization: 'Basic '+ btoa(clientAuth.username+ ":" +clientAuth.password)
        },
        body : formData
    };

    return fetch('/oauth/check_token',requestOptions)
        .then(handleResponse)
        .then(result => {
            console.log("token check success")
            return result;
        }).catch(error =>{
            return error;
        });
}

//OAuth2 로그인 수행
function login(email, password) {
    const grant_type = "password";

    const formData = new FormData();
    formData.append('grant_type',grant_type);
    formData.append('username',email);
    formData.append('password',password);

    const requestOptions = {
        method: 'POST',
        headers: {
            Authorization: 'Basic '+ btoa(clientAuth.username+ ":" +clientAuth.password)
        },
        body: formData

    };

    return fetch(`/oauth/token`, requestOptions)
        .then(handleResponse)
        .then(token => {
            const result = {
                userId : email,
                access_token : token.access_token,
                refresh_token : token.refresh_token
            }
            localStorage.setItem('user',JSON.stringify(result))
            return result;
        });
}

/*
function getUser(){
    const token = JSON.parse(localStorage.getItem('user'))['access_token'];

    const userRequest = {
        method:'GET',
        headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer '+token
        }
    }
    //get user 정보
    return fetch("/api/user",userRequest)
        .then(handleResponse)
        .then(user => {
            return user;
        });
}
*/

function logout() {
    // remove user from local storage to log user out
    localStorage.removeItem('user');
}

function register(user) {
    const requestOptions = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(user)
    };
    return fetch(`/users/register`, requestOptions).then(handleResponse);
}

function getAll() {
    const requestOptions = {
        method: 'GET',
        headers: authHeader()
    };
    return fetch(`/users`, requestOptions).then(handleResponse);
}

function handleResponse(response) {
    return response.text().then(text => {
        const data = text && JSON.parse(text);
        if (!response.ok) {
            if (response.status === 401) {
                // auto logout if 401 response returned from api
                logout();
                //location.reload(true);
            }
            const error = (data && data.message) || response.statusText;
            return Promise.reject(error);
        }
        return data;
    });
}
