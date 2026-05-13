import axios from 'axios'
import { errorMessage, isBackendRequest, markBackendOffline, markBackendOnline } from './backendHealth'

export const http = axios.create({
    baseURL: '/',
    timeout: 30_000,
})

http.interceptors.response.use(
    response => {
        if (isBackendRequest(response.config.url)) {
            markBackendOnline()
        }
        return response
    },
    error => {
        const message = errorMessage(error)
        if (isBackendRequest(error.config?.url)) {
            if (error.response) {
                markBackendOnline()
            } else {
                markBackendOffline(message)
            }
        }
        return Promise.reject(new Error(message))
    },
)
