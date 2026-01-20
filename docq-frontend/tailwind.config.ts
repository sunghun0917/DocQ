/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{vue,js,ts,jsx,tsx}", // src 안의 모든 vue 파일을 감시함
    ],
    theme: {
        extend: {},
    },
    plugins: [],
}