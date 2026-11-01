import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { initTheme } from "./lib/theme";
import { bootstrapAuth } from "./services/bootstrapAuth";
import { store } from "./store";
import "./main.css";

// This is the very first file that runs when the app loads in the browser.
// Set the light/dark theme on the page BEFORE anything else, so the page
// never flashes the wrong theme for a split second.
initTheme();

// Also check "am I already signed in?" BEFORE drawing the app on screen.
// Without this, the app could briefly show the login page even for someone
// who's already signed in, then flicker over to their dashboard once the
// check finishes.
void bootstrapAuth().then(() => {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <Provider store={store}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </Provider>
    </StrictMode>,
  );
});
