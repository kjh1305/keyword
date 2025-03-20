import Vue from 'vue'
import VueRouter from 'vue-router'
import VueMeta from 'vue-meta'

import routes from './routes'
//import {loginService} from "../helpers/authservice/login.service";

Vue.use(VueRouter)
Vue.use(VueMeta, {
    // The component option name that vue-meta looks for meta info on.
    keyName: 'page',
})

const router = new VueRouter({
    routes,
    // Use the HTML5 history API (i.e. normal-looking routes)
    // instead of routes with hashes (e.g. example.com/#/about).
    // This may require some server configuration in production:
    // https://router.vuejs.org/en/essentials/history-mode.html#example-server-configurations
    mode: 'history',
    // Simulate native-like scroll behavior when navigating to a new
    // route and using back/forward buttons.
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition
        } else {
            return {x: 0, y: 0}
        }
    },
})

//페이지 이동시에 체크
router.beforeEach((routeTo, routeFrom, next) => {
    const publicPages = ['/login', '/register', '/forgot-password'];
    const authpage = !publicPages.includes(routeTo.path);
    const loggeduser = localStorage.getItem('user');

    //user 비교 실패 && 경로명이 제대로 안된경우.
    if (authpage && !loggeduser) {
        //return next('/');
    }

    /*//페이지 이동시 유효성 체크
    if (loggeduser) {
        loginService.checkToken().then(result => {

            if (typeof result === "string") {//에러발생
                if (result === "Token has expired") {//access_token 만료됨

                    //refresh 토큰으로 새로운 access_token 발급
                    loginService.getNewToken().then(result => {
                        if (typeof result === "string") {//에러 발생한 경우에만 result 값이 문자열
                            loginService.logout();
                            return next('/login');
                        }
                        //로컬 스토리지에 새로운 값 저장
                        let obj = JSON.parse(localStorage.getItem('user'))
                        obj['access_token'] = result.access_token;
                        localStorage.setItem('user', JSON.stringify(obj))
                    })
                } else { //에러 발생시 로그아웃
                    loginService.logout();
                    return next('/login');
                }
            }
        })
    }*/

    next();

})

router.beforeResolve(async (routeTo, routeFrom, next) => {
    // Create a `beforeResolve` hook, which fires whenever
    // `beforeRouteEnter` and `beforeRouteUpdate` would. This
    // allows us to ensure data is fetched even when params change,
    // but the resolved route does not. We put it in `meta` to
    // indicate that it's a hook we created, rather than part of
    // Vue Router (yet?).
    try {
        // For each matched route...
        for (const route of routeTo.matched) {
            await new Promise((resolve, reject) => {
                // If a `beforeResolve` hook is defined, call it with
                // the same arguments as the `beforeEnter` hook.
                if (route.meta && route.meta.beforeResolve) {
                    route.meta.beforeResolve(routeTo, routeFrom, (...args) => {
                        // If the user chose to redirect...
                        if (args.length) {
                            // If redirecting to the same route we're coming from...
                            // Complete the redirect.
                            next(...args)
                            reject(new Error('Redirected'))
                        } else {
                            resolve()
                        }
                    })
                } else {
                    // Otherwise, continue resolving the route.
                    resolve()
                }
            })
        }
        // If a `beforeResolve` hook chose to redirect, just return.
    } catch (error) {
        return
    }

    // If we reach this point, continue resolving the route.
    next()
})

export default router
