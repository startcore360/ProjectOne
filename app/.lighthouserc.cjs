module.exports = {
  ci: {
    collect: {
      startServerCommand: "npm run preview -- --host 127.0.0.1 --port 4173",
      startServerReadyPattern: "Local:",
      startServerReadyTimeout: 30000,
      url: ["http://127.0.0.1:4173/"],
      numberOfRuns: 1,
    },
    upload: {
      target: "filesystem",
      outputDir: ".lighthouseci",
    },
  },
};
