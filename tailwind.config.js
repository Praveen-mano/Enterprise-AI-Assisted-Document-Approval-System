export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "Segoe UI", "sans-serif"]
      },
      colors: {
        ink: "#07111f",
        panel: "#0d1b2f",
        cyanx: "#38d5ff",
        mintx: "#4ff0bb",
        amberx: "#f8c14a",
        rosex: "#ff6286"
      },
      boxShadow: {
        glow: "0 0 32px rgba(56, 213, 255, 0.22)",
        soft: "0 24px 80px rgba(0, 0, 0, 0.28)"
      }
    }
  },
  plugins: []
};
