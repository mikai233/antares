import {createRouter, createWebHistory} from 'vue-router'

export const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            component: () => import('@/views/DashboardView.vue'),
            meta: {title: '运行总览'},
        },
        {
            path: '/scripts',
            component: () => import('@/views/ScriptConsoleView.vue'),
            meta: {title: '脚本任务'},
        },
        {
            path: '/script-jobs',
            component: () => import('@/views/ScriptJobsView.vue'),
            meta: {title: '任务记录'},
        },
    ],
})
