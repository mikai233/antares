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
        {
            path: '/patches',
            component: () => import('@/views/PatchView.vue'),
            meta: {title: '运行补丁'},
        },
        {
            path: '/config',
            component: () => import('@/views/ConfigView.vue'),
            meta: {title: '配置中心'},
        },
        {
            path: '/cluster',
            component: () => import('@/views/ClusterView.vue'),
            meta: {title: '集群管理'},
        },
        {
            path: '/game-time',
            component: () => import('@/views/GameTimeView.vue'),
            meta: {title: '游戏时间'},
        },
        {
            path: '/shutdown',
            component: () => import('@/views/ShutdownView.vue'),
            meta: {title: '停服控制'},
        },
        {
            path: '/gm-metadata',
            component: () => import('@/views/GmMetadataView.vue'),
            meta: {title: 'GM 元数据'},
        },
    ],
})
