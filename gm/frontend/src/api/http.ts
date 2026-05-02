import axios from 'axios'

export const http = axios.create({
    baseURL: '/',
    timeout: 30_000,
})

http.interceptors.response.use(
    response => response,
    error => {
        const message = error.response?.data?.message ?? error.message ?? 'Request failed'
        return Promise.reject(new Error(message))
    },
)
