const processedImgEl = document.getElementById("processedFrame") as HTMLImageElement | null;
const statsEl = document.getElementById("stats") as HTMLElement | null;

const params = new URLSearchParams(window.location.search);
const frameUrlParam = params.get("url");
const FRAME_URL = frameUrlParam ?? "http://localhost:8080/frame";

function updateStatsText(text: string): void {
  if (statsEl) {
    statsEl.textContent = text;
  }
}

function refreshFrame(): void {
  if (!processedImgEl) {
    console.warn("Processed image element not found");
    return;
  }
  const ts = Date.now();
  processedImgEl.src = `${FRAME_URL}?t=${ts}`;
  updateStatsText(`Source: ${FRAME_URL} | Updated: ${new Date(ts).toLocaleTimeString()}`);
}

if (processedImgEl) {
  processedImgEl.crossOrigin = "anonymous";
  processedImgEl.onerror = () => {
    updateStatsText("Failed to load frame. Set ?url=http://<device-ip>:8080/frame");
    stopPolling();
    import("./assets/sampleFrame.js").then(({ default: frameData }) => {
      processedImgEl.src = frameData;
      updateStatsText("Showing sample image. Provide ?url to connect to device.");
    }).catch(() => {
      // ignore
    });
  };
}

refreshFrame();

let intervalId: number | undefined;
function startPolling() {
  intervalId = window.setInterval(() => {
    if (document.visibilityState === "visible") {
      refreshFrame();
    }
  }, 1000);
}

function stopPolling() {
  if (intervalId) {
    window.clearInterval(intervalId);
    intervalId = undefined;
  }
}

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    startPolling();
  } else {
    stopPolling();
  }
});

startPolling();

