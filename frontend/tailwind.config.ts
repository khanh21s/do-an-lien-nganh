import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/app/**/*.{ts,tsx}', './src/components/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef7ff',
          100: '#d9edff',
          200: '#bce0ff',
          300: '#8ecdff',
          400: '#59b0ff',
          500: '#3390fc',
          600: '#1d70f0',
          700: '#1859dd',
          800: '#1a48b3',
          900: '#1b408d',
        },
        ink: {
          50: '#eef1f8',
          100: '#dbe0ee',
          200: '#b3bdd8',
          300: '#8896bd',
          400: '#5c6a97',
          500: '#3d4a76',
          600: '#2b3760',
          700: '#212b4d',
          800: '#171f3a',
          900: '#12182e',
          950: '#0b0f1e',
        },
      },
      backgroundImage: {
        'pro-gradient': 'linear-gradient(135deg, #8b5cf6 0%, #3390fc 100%)',
      },
      // T15 game: "+25" floating above the score after a correct answer
      keyframes: {
        'float-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '20%': { opacity: '1' },
          '100%': { opacity: '0', transform: 'translateY(-18px)' },
        },
      },
      animation: {
        'float-up': 'float-up 0.9s ease-out forwards',
      },
    },
  },
  plugins: [],
};

export default config;
