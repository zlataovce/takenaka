/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const themeKey = "_theme";

const toggleTheme = () => setTheme(getTheme() === "light" ? "dark" : "light");
const getTheme = () => localStorage.getItem(themeKey) || (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
const setTheme = (theme) => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(themeKey, theme);
};

window.addEventListener("DOMContentLoaded", () => document.documentElement.setAttribute("data-theme", getTheme()));

const getVersionBaseUrl = () => {
    const path = window.location.pathname.substring(1);
    if (path) {
        const parts = [];
        for (const part of path.split("/")) {
            parts.push(part);

            // FIXME: this won't work with snapshot versions
            if (!part.endsWith(".html") && part.includes(".")) {
                return "/" + parts.join("/");
            }
        }
    }

    return null;
};

const baseUrl = getVersionBaseUrl();

// a map of "<namespace>" to "<namespace badge color>"
let colors = {};
// a list of { "<namespace>": <mapping string or null> }
let classIndex = [];

// TODO: this is a hack, figure this out?
const initialIndexLoadPromise = new Promise((resolve, _) => {
    document.addEventListener("indexUpdate", resolve, { once: true });
});

const updateClassIndex = (indexString) => {
    indexString = indexString.replaceAll("%nm", "net/minecraft").replaceAll("%cm", "com/mojang");

    const header = [];
    for (const line of indexString.match(/[^\r\n]+/g)) {
        if (header.length === 0) {
            for (const col of line.split("\t")) {
                const colParts = col.split(":", 2);

                header.push(colParts[0]);
                colors[colParts[0]] = colParts[1];
            }
        } else {
            const obj = {};

            for (const [index, col] of line.split("\t", header.length).entries()) {
                obj[header[index]] = col || null;
            }

            classIndex.push(obj);
        }
    }

    document.dispatchEvent(new Event("indexUpdate"));
};

// dynamically load class index, but async
window.addEventListener("DOMContentLoaded", () => {
    if (baseUrl) {
        const indexScript = document.createElement("script");
        indexScript.async = true;
        indexScript.src = `${baseUrl}/class-index.js`;

        document.head.appendChild(indexScript);
    }
});

const searchNamespacesKey = "_namespaces";

const getStoredSearchNamespaces = () => {
    const nsString = localStorage.getItem(searchNamespacesKey);

    return nsString ? nsString.split(",") : (nsString != null ? [] : null);
};
let searchNamespaces = getStoredSearchNamespaces();

const updateSearchNamespaces = (newNamespaces) => {
    searchNamespaces = newNamespaces;
    localStorage.setItem(searchNamespacesKey, newNamespaces.join(","));
};

const search = (query) => {
    const resultsBox = document.getElementById("search-results-box");

    query = query.replaceAll(".", "/").toLowerCase().trim();
    if (!query) {
        resultsBox.replaceChildren();
        return;
    }

    const results = [];
    const hasPackage = query.includes("/");

    klassLoop:
    for (const klass of classIndex) {
        for (const ns in klass) {
            if (!searchNamespaces.includes(ns)) continue;

            const klassName = klass[ns];
            if (klassName) {
                if (!klassName.toLowerCase().includes(query)) continue;

                const lastSlashIndex = klassName.lastIndexOf("/");
                const simpleName = lastSlashIndex !== -1 ? klassName.substring(lastSlashIndex + 1) : klassName;

                // if a package is not specified, match only against simple class names, not fully qualified ones
                if (!hasPackage && !simpleName.toLowerCase().includes(query)) continue;

                // more similar = lower number
                results.push({
                    klass: klass,
                    ns: ns,
                    simpleName: simpleName,
                    packageName: lastSlashIndex !== -1 ? klassName.substring(0, lastSlashIndex).replaceAll("/", ".") : null,
                    similarity: (hasPackage ? klassName.length : simpleName.length) - query.length
                });

                // only show a class once, skip the other namespaces
                continue klassLoop;
            }
        }
    }

    results.sort((a, b) => a.similarity - b.similarity);
    resultsBox.replaceChildren(...(
        // limit results to 50, should be plenty
        results.slice(0, Math.min(results.length, 50)).map((r) => {
            const resultElem = document.createElement("div");
            resultElem.classList.add("search-result");
            resultElem.addEventListener("click", () => {
                window.location.pathname = `${baseUrl}/${Object.values(r.klass).find((e) => e != null)}.html`;
            });

            const title = document.createElement("p");
            title.classList.add("search-result-title");
            title.innerText = r.simpleName;
            resultElem.appendChild(title);

            if (r.packageName) {
                const packageSubtitle = document.createElement("p");
                packageSubtitle.classList.add("search-result-subtitle");
                packageSubtitle.innerText = `package: ${r.packageName}`;
                resultElem.appendChild(packageSubtitle);
            }

            const nsSubtitle = document.createElement("p");
            nsSubtitle.classList.add("search-result-subtitle");
            nsSubtitle.innerHTML = `namespace: <span style="color:${colors[r.ns]}">${r.ns}</span>`;
            resultElem.appendChild(nsSubtitle);

            return resultElem;
        })
    ));
};

const toggleOptions = () => {
    const optionBox = document.getElementById("option-box");
    optionBox.style.display = optionBox.style.display === "grid" ? "none" : "grid";
};

const updateOptions = () => {
    const searchInput = document.getElementById("search-input");
    const optionBox = document.getElementById("option-box");

    if (!searchNamespaces) {
        searchNamespaces = Object.keys(colors);
    }
    optionBox.replaceChildren(...(
        Object.entries(colors).map(([ns, color]) => {
            const labelElem = document.createElement("label");
            labelElem.style.color = color;

            const inputElem = document.createElement("input");
            inputElem.type = "checkbox";
            inputElem.checked = searchNamespaces.includes(ns);
            inputElem.addEventListener("change", () => {
                updateSearchNamespaces(inputElem.checked ? [...searchNamespaces, ns] : searchNamespaces.filter((e) => e !== ns));

                // manually refresh search results
                searchInput.dispatchEvent(new Event("input"));
            });

            labelElem.appendChild(inputElem);
            labelElem.appendChild(document.createTextNode(ns));

            return labelElem;
        })
    ));
};
