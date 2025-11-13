const frameElement = document.getElementById("processedFrame") as HTMLImageElement;
const statsElement = document.getElementById("stats") as HTMLElement;

const metadata = {
  fps: 18.5,
  resolution: "640x480",
  timestamp: new Date().toISOString()
};

function loadFrame(): void {
  import("./assets/sampleFrame").then(({ default: frameData }) => {
    frameElement.src = frameData;
    updateStats(metadata);
  }).catch((error) => {
    console.error("Failed to load sample frame", error);
  });
}

function updateStats(data: typeof metadata): void {
  statsElement.textContent = `FPS: ${data.fps.toFixed(1)} | Resolution: ${data.resolution} | Captured: ${data.timestamp}`;
}

loadFrame();

