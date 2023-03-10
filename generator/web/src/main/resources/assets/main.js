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

window.addEventListener("load", () => document.documentElement.setAttribute("data-theme", getTheme()));

const getVersionBaseUrl = () => {
    const path = window.location.pathname.substring(1);
    if (path) {
        const parts = [];
        for (const part of path.split("/")) {
            parts.push(part);
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
};

const search = (query) => {
    const resultsBox = document.getElementById("search-results-box");

    query = query.trim();

    const results = [];
    if (query) {
        const predicates = [];

        let newQuery = "";
        for (const option of query.split(" ")) {
            const optionParts = option.split(":", 2);
            if (optionParts.length === 2) {
                switch (optionParts[0]) {
                    case "namespace":
                    case "type":
                    case "ns":
                        const namespaceTarget = optionParts[1].toLowerCase();
                        predicates.push((klass, ns, klassName) => ns.toLowerCase() === namespaceTarget);
                        break;

                    // add more search options here
                }
            } else {
                newQuery = newQuery + option;
            }
        }

        newQuery = newQuery.replaceAll(".", "/").toLowerCase();

        for (const klass of classIndex) {
            for (const ns in klass) {
                const klassName = klass[ns];

                // limit to 5 results
                if (results.length <= 5 && klassName) {
                    if (!klassName.toLowerCase().includes(newQuery)) continue;
                    if (!predicates.every((p) => p(klass, ns, klassName))) continue;

                    const resultElem = document.createElement("div");
                    resultElem.classList.add("search-result");
                    resultElem.addEventListener("click", () => {
                        window.location.pathname = `${baseUrl}/${Object.values(klass).find((e) => e != null)}.html`;
                    });

                    const lastSlashIndex = klassName.lastIndexOf("/");
                    const klassPackage = lastSlashIndex !== -1 ? klassName.substring(0, lastSlashIndex).replaceAll("/", ".") : null;
                    const simpleKlassName = lastSlashIndex !== -1 ? klassName.substring(lastSlashIndex + 1) : klassName;

                    const title = document.createElement("p");
                    title.classList.add("search-result-title");
                    title.innerText = simpleKlassName;
                    resultElem.appendChild(title);

                    if (klassPackage) {
                        const packageSubtitle = document.createElement("p");
                        packageSubtitle.classList.add("search-result-subtitle");
                        packageSubtitle.innerText = `package: ${klassPackage}`;
                        resultElem.appendChild(packageSubtitle);
                    }

                    const nsSubtitle = document.createElement("p");
                    nsSubtitle.classList.add("search-result-subtitle");
                    nsSubtitle.innerHTML = `namespace: <span style="color:${colors[ns]}">${ns}</span>`;
                    resultElem.appendChild(nsSubtitle);

                    results.push(resultElem);
                }
            }
        }
    }

    resultsBox.replaceChildren(...results);
};
