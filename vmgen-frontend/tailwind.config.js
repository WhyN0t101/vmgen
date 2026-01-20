/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          500: '#0EA5E9',  // Electric blue
          900: '#0c4a6e',
        },
        warning: {
          500: '#F59E0B',  // Amber
        },
        danger: {
          500: '#EF4444',  // Red
        },
        success: {
          500: '#10B981',  // Green
        },
        slate: {
          950: '#0F172A',  // Background
        }
      }
    }
  },
  plugins: [],
}



