const themeKey = "_theme";

const toggleTheme = () => setTheme(getTheme() === "light" ? "dark" : "light");
const getTheme = () => localStorage.getItem(themeKey) || (window.matchMedia("(prefers-color-scheme: light)") ? "light" : "dark");
const setTheme = (theme) => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(themeKey, theme);
};

window.addEventListener("load", () => document.documentElement.setAttribute("data-theme", getTheme()));
