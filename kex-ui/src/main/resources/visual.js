var h = (window.innerHeight - $('.logo').height() - $('.spinner').height() - 50) / 2
document.querySelector('.spinner').style.margin = h + "px auto"

const host = document.location.host

let width = window.innerWidth,
    height = window.innerHeight - $('.logo').height() - 70;

var sheet = window.document.styleSheets[window.document.styleSheets.length - 1];
sheet.addRule('.nice-select-dropdown', `width: ${width - 20}px`)
sheet.addRule('.option', `width: ${width - 20}px`);

const traces = []

function openNav() {
    document.getElementById("mySidebar").style.width = width +"px";
}

function closeNav() {
    document.getElementById("mySidebar").style.width = "0px";
}

let socket = new WebSocket(`ws://${host}/`);

function newResponse(code, message) {
    return JSON.stringify({code: code, message: message})
}

socket.onopen = function () {
    socket.send(newResponse(3, "Get jar name"));
};

socket.onmessage = function (event) {
    const data = JSON.parse(event.data);
    switch (data.code) {
        case 2:
            traces.push({index: traces.length, name: data.message})
            newTrace(data.message)
            // new trace
            break;
        case 20:
            // show trace
            break;
        case 3:
            localStorage.setItem("file", data.message);
            break;
        default:
            break;
    }
};

socket.onclose = function (event) {
    if (event.wasClean) {
    } else {
    }
};

socket.onerror = function (error) {
};

const req = new XMLHttpRequest();
req.open("GET", `http://${host}/` + localStorage.getItem("file") + "/" + localStorage.getItem("file"), true);
req.onreadystatechange = function () {
    if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
        let response = JSON.parse(req.response, parseJson);
        graphIt(response.message)
    }
}
req.send(null);

const methods = new XMLHttpRequest();
methods.open("GET", `http://${host}/` + localStorage.getItem("file") + "/" + localStorage.getItem("file") + "-all", true);
methods.onreadystatechange = function () {
    if (methods.readyState === XMLHttpRequest.DONE && methods.status === 200) {
        let sidebar = document.querySelector('#seachable-select');
        JSON.parse(methods.response).methods.forEach(method => {
            let option = document.createElement("option");
            //a.setAttribute("class", "method-link");
            //a.setAttribute("onclick", "anotherMethod(\'" + method + "\',\'" + localStorage.getItem("file") + "\');");
            //a.setAttribute("href", "#");
            option.innerHTML = method.replace("<", "").replace(">", "");
            sidebar.appendChild(option);
        });
        var options = {searchable: true};
        NiceSelect.bind(document.getElementById("seachable-select"), options);
        document.getElementById("seachable-select").remove()
        document.querySelectorAll('li').forEach(li => {
            if (li) {
                let text = document.createElement("text");
                text.innerText = li.innerHTML
                text.setAttribute('class', 'method')
                li.innerHTML = ""
                li.appendChild(text)
            }
        })
    }
}
methods.send(null);

function change(el) {
    const method = el.innerText
    const jar = localStorage.getItem("file")
    graph.clear()
    document.querySelector('.spinner').style.display = "block";

    req.open("GET", `http://${host}/` + jar + "/" + method, true);
    closeNav();
    req.send(null);
}

G6.registerNode(
    'node',
    {
        drawShape(cfg, group) {
            const rect = group.addShape('rect', {
                attrs: {
                    x: 0,
                    y: 0,
                    width: 10,
                    height: 10,
                    radius: 10,
                    stroke: '#5B8FF9',
                    fill: '#C6E5FF',
                    lineWidth: 3,
                },
                name: 'rect-shape',
            });
            if (cfg.name) {
                const label = group.addShape('text', {
                    attrs: {
                        text: cfg.name,
                        x: 0,
                        y: 0,
                        fill: '#00287E',
                        fontSize: 14,
                        textAlign: 'center',
                        textBaseline: 'middle',
                        fontWeight: 'bold',
                    },
                    name: 'text-shape',
                });
                const labelBBox = label.getBBox({'stroke': true});
                labelBBox.width += 6
                labelBBox.height += 6
                rect.attrs.x = -labelBBox.width / 2
                rect.attrs.y = -labelBBox.height / 2
                rect.attrs.height = labelBBox.height
                rect.attrs.width = labelBBox.width
            }
            return rect;
        },
    },
    'single-node',
);

const graph = new G6.Graph({
    container: 'container',
    width,
    height,
    layout: {
        type: 'dagre',
        ranksep: 25,
        controlPoints: true,
        nodesepFunc: (node) => {
            return G6.Util.getTextSize(node.name.split("\n").sort(
                function (a, b) {
                    return b.length - a.length;
                }
            )[0], 14)[0] / 2.5 //fontSize
        }
    },
    defaultNode: {
        type: 'node',
    },
    defaultEdge: {
        type: 'polyline',
        style: {
            radius: 20,
            offset: 45,
            endArrow: true,
            lineWidth: 2,
            stroke: '#C2C8D5',
        },
    },
    nodeStateStyles: {
        selected: {
            stroke: '#d9d9d9',
            fill: '#5394ef',
        },
    },
    modes: {
        default: [
            'drag-canvas',
            {
                type: 'zoom-canvas',
                minZoom: 0.0001,
            },
            'click-select',
            /*{
                type: 'tooltip',
                formatText(model) {
                    const cfg = model.conf;
                    const text = [];
                    if (cfg) {
                        cfg.forEach((row) => {
                            text.push(row.label + ':' + row.value + '<br>');
                        });
                    }
                    return text.join('\n');
                },
                offset: 30,
            },*/
        ],
    },
});

graph.setMinZoom(0.001);

graph.on("afterlayout", () => {
    if (parent) {
        graph.focusItem(parent)
        parent = null
        return
    }
    let node = graph.find('node', (n) => {
        let node = n.getModel()
        return n.getInEdges().length === 0 && node.name.replaceAll("/", ".") === localStorage.getItem("method").split("::").slice(1).join("::")
    })
    graph.focusItem(node);
    graph.translate(0, -height / 2 + node.getBBox().y)
})

let parent;

graph.on('node:contextmenu', (evt) => {
    parent = evt.item
    evt.originalEvent.preventDefault()
    let contextElement = document.getElementById("context-menu");
    contextElement.style.top = evt.clientY + "px";
    contextElement.style.left = evt.clientX + "px";
    contextElement.classList.add("active");
    localStorage.setItem("subMethod", parent.getModel().name)
})

graph.on('click', (evt) => {
    if (evt.target.innerText !== "☰") closeNav();
    document.getElementById("context-menu").classList.remove("active");
})

graph.on('contextmenu', (evt) => {
    evt.preventDefault()
})

function graphIt(json) {
    document.querySelector('.spinner').style.display = "none";
    const data = {'nodes': json.nodes, 'edges': json.links}
    localStorage.setItem("method", json.name)

    graph.changeData(data);
    /*if (typeof window !== 'undefined')
        window.onresize = () => {
            if (!graph || graph.get('destroyed')) return;
            if (!container || !container.scrollWidth || !container.scrollHeight) return;
            graph.changeSize(container.scrollWidth, container.scrollHeight);
        };*/
}

function expand() {
    document.getElementById("context-menu").classList.remove("active");
    const jar = localStorage.getItem("file")
    const method = localStorage.getItem("method")
    const subMethod = escapeURL(localStorage.getItem("subMethod"))
    const expandReq = new XMLHttpRequest();
    expandReq.onreadystatechange = function () {
        if (expandReq.readyState === XMLHttpRequest.DONE && expandReq.status === 200) {
            document.querySelector("body").style.pointerEvents = "all"
            let response = JSON.parse(expandReq.response)
            response = response.code !== 10 ? JSON.parse(expandReq.response, parseJson) : response
            document.querySelector('.spinner').style.display = "none";
            document.querySelector('.graph-pane').style.display = "block";
            if (response.code === 10) {
                iziAlert(response.message)
                return;
            }
            const name = response.message.name.split("::").slice(1).join("::")
            let lastId = parseInt(graph.getNodes().length)
            let outEdges = parent.getOutEdges()
            let alreadyExpanded = outEdges.find(outEdge => {
                return outEdge.getTarget().getModel().name.replaceAll("/", ".") === name
            })
            if (alreadyExpanded) {
                iziAlert("Already expanded")
                return
            }
            outEdges.forEach(edge => {
                if (edge) parent.removeEdge(edge)
            })
            response.message.nodes.forEach(node => {
                if (node) {
                    node.id = (parseInt(node.id) + lastId).toString()
                    graph.addItem('node', node)
                }
            })
            let start = response.message.nodes.find(node => {
                return node.name.replaceAll("/", ".") === name
            })
            graph.addItem('edge', {source: parent.getID(), target: start.id})
            response.message.links.forEach(link => {
                if (link) {
                    link.source = (parseInt(link.source) + lastId).toString()
                    link.target = (parseInt(link.target) + lastId).toString()
                    graph.addItem('edge', link)
                }
            })
            let ends = response.message.nodes.filter(node => {
                return node.name.includes("return")
            })
            ends.forEach(end => {
                if (end) {
                    outEdges.forEach(outEdge => {
                        if (outEdge) {
                            graph.addItem('edge', {source: end.id, target: outEdge.getTarget().getID()})
                            graph.removeItem(outEdge)
                        }
                    })
                }
            })
            graph.layout()
        }
    }
    expandReq.open("GET", `http://${host}/` + jar + "/" + method + "/" + subMethod, true);
    document.querySelector("body").style.pointerEvents = "none";
    document.querySelector('.spinner').style.display = "block";
    document.querySelector('.graph-pane').style.display = "none";
    expandReq.send(null)
}

function escapeURL(str) {
    return str
        .replaceAll("%", "%25")
        .replaceAll("\n", "%0A")
        .replaceAll("/", "%2F")
        .replaceAll("=", "%3D")
        .replaceAll(":", "%3A")
        .replaceAll("    ", "")
        .replaceAll("\"", "'")
        .replaceAll("\\", "%5C")
}

function parseJson(k, value) {
    if (k === '') {
        return value;
    }
    return JSON.parse(value);
}

function newTrace(name) {
    iziToast.success({
        id: 'success',
        title: 'New trace',
        message: name,
        iconUrl: 'info.svg',
        timeout: false,
        progressBar: false,
        onClosed: function () {
            //alert("Closed")
        },
        buttons: [
            ['<button><b>Show</b></button>', function (instance, toast) {

                instance.hide({ transitionOut: 'flipOutX' }, toast, 'button');

            }, true]
        ],
        transitionIn: 'fadeInUp',
        transitionOut: 'fadeOutDown'
    });
}

function iziAlert(text) {
    iziToast.warning({
        id: 'warning',
        title: 'Warning',
        message: text,
        iconUrl: 'info.svg',
        timeout: 5000,
        progressBar: false,
        onClosed: function () {
            //alert("Closed")
        },
        transitionIn: 'fadeInUp',
        transitionOut: 'fadeOutDown'
    });
}