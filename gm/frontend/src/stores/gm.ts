import {defineStore} from 'pinia'

export const useGmStore = defineStore('gm', {
    state: () => ({
        activeCluster: 'local',
        operator: 'developer',
    }),
})
