const host = document.location.host

$(function () {
    $("#dialog").dialog({
        autoOpen: false,
        modal: true,
        buttons: {
            Ok: function () {
                $(this).dialog("close");
            }
        }
    });
});

function openNav() {
    document.getElementById("mySidebar").style.width = "300px";
}

function closeNav() {
    document.getElementById("mySidebar").style.width = "0px";
}

let json;

let socket = new WebSocket(`ws://${host}/`);


function newResponse(code, message) {
    return JSON.stringify({code: code, message: message})
}

socket.onopen = function (e) {
    socket.send(newResponse(2, "Get jar name"));
};

socket.onmessage = function (event) {
    const data = JSON.parse(event.data);
    if (data.code === 2)
        localStorage.setItem("file", data.message);
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
        var response = JSON.parse(req.response, parseJson)
        graphIt(response.message)
    }
}
req.send(null);

const methods = new XMLHttpRequest();
methods.open("GET", `http://${host}/` + localStorage.getItem("file") + "/" + localStorage.getItem("file") + "-all", true);
methods.onreadystatechange = function () {
    if (methods.readyState === XMLHttpRequest.DONE && methods.status === 200) {
        let sidebar = document.querySelector('.sidebar');
        JSON.parse(methods.response).methods.forEach(method => {
            let a = document.createElement("a");
            a.setAttribute("class", "method-link");
            a.setAttribute("onclick", "anotherMethod(\'" + method + "\',\'" + localStorage.getItem("file") + "\');");
            a.setAttribute("href", "#");
            a.innerHTML = method.replace("<", "").replace(">", "");
            sidebar.appendChild(a);
        });
    }
}
methods.send(null);

let width = window.innerWidth,
    height = window.innerHeight - $('.logo').height() - 70;

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
    fitCenter: true,
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
            'zoom-canvas',
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
    fitView: true,
});

const defaultData = graph.save()

function graphIt(json) {
    document.querySelector('.spinner').style.display = "none";
    const data = {'nodes': json.nodes, 'edges': json.links}
    localStorage.setItem("method", json.name)

    graph.changeData(data);
    let node = graph.find('node', (n) => {
        let node = n.getModel()
        return n.getInEdges().length === 0 && node.name.replaceAll("/", ".") === localStorage.getItem("method").split("::").slice(1).join("::")
    })
    graph.focusItem(node);
    graph.translate(0, -height / 2 + node.getBBox().y)

    /*if (typeof window !== 'undefined')
        window.onresize = () => {
            if (!graph || graph.get('destroyed')) return;
            if (!container || !container.scrollWidth || !container.scrollHeight) return;
            graph.changeSize(container.scrollWidth, container.scrollHeight);
        };*/
}

let graphviz;

function anotherMethod(method, jar) {
    graph.changeData(defaultData);
    document.querySelector('.spinner').style.display = "block";

    req.open("GET", `http://${host}/` + jar + "/" + method, true);
    closeNav();
    req.send(null);
}

function expand(jar, method, subMethod) {
    const expandReq = new XMLHttpRequest();
    expandReq.onreadystatechange = function () {
        if (expandReq.readyState === XMLHttpRequest.DONE && expandReq.status === 200) {
            document.querySelector("body").style.pointerEvents = "all"
            /*if (expandReq.response.includes("Can't expand")) {
                afterWaitingWithDialog(expandReq.response)
            } else if (expandReq.response.includes("Has already been expanded")) {
                afterWaitingWithDialog(expandReq.response)
            } else {*/
            const response = JSON.parse(req.response, parseJson);
            document.querySelector('.spinner').style.display = "none";
            document.querySelector('.graph-pane').style.display = "block";

            //}
        }
    }
    expandReq.open("GET", `http://${host}/` + jar + "/" + method + "/" + subMethod, true);
    document.querySelector("body").style.pointerEvents = "none";
    document.querySelector('.spinner').style.display = "block";
    document.querySelector('.graph-pane').style.display = "none";
    expandReq.send(null)
}


function afterWaitingWithDialog(text) {
    document.querySelector('.spinner').style.display = "none";
    document.querySelector('.graph-pane').style.display = "block";
    document.getElementById("dialog").style.display = "block";
    document.getElementById("dialog").innerText = text;
    $("#dialog").dialog("open");
}

function parseJson(k, value) {
    if (k === '') {
        return value;
    }
    return JSON.parse(value);
}

/*function start(j) {
    json = j;
    document.querySelector('.spinner').style.display = "none";

    let width = window.innerWidth,
        height = window.innerHeight - $('.logo').height() - 70;
    console.log(height);

    function transitionFactory() {
        return d3.transition("main").ease(d3.easeExpInOut).duration(1000);
    }

    graphviz = d3.select(".graph-pane")
        .graphviz()
        .attributer(attributer)
        .transition(transitionFactory)
        .tweenShapes(true);


    graphviz.renderDot(json);

    function attributer(datum) {
        let selection = d3.select(this);
        if (datum.tag === "svg") {
            //var width = d3.select('.graph-pane').node().clientWidth;
            //var height = d3.select('.graph-pane').node().clientHeight;
            const unit = 'px';
            selection
                .attr("width", width + unit)
                .attr("height", height + unit);
            datum.attributes.width = width + unit;
            datum.attributes.height = height + unit;
        }
    }

    d3.select(document).on("click", function () {
        console.log(d3.event.target);
        if (d3.event.target.innerText !== "☰") closeNav();
        document.getElementById("context-menu").classList.remove("active");
    })

    d3.select(document).on("contextmenu", function () {
        console.log(d3.event.target);
        d3.event.preventDefault();
        if (d3.event.target.tagName === "text") {
            let contextElement = document.getElementById("context-menu");
            contextElement.style.top = d3.event.offsetY + $('.logo').height() + 70 + "px";
            contextElement.style.left = d3.event.offsetX + "px";
            contextElement.classList.add("active");
            Array.from(document.getElementsByClassName("item")).filter(it => {
                return it.innerText === "Expand"
            })[0].setAttribute("onClick",
                "expand(\'"
                + localStorage.getItem("file")
                + "\', \'"
                + localStorage.getItem("method")
                + "\', \'"
                + d3.event.target.textContent
                    .replaceAll("%", "%25")
                    .replaceAll("/", "%5C")
                    .replaceAll("=", "%3D")
                    .replaceAll(":", "%3A")
                    .replaceAll("    ", "")
                    .replaceAll("\"", "\\\"") + "\')");
        }

    });
}



function removeGraphName(graph) {
    let resp = graph.split(" ");
    for (const [i, s] of resp.entries()) {
        if (s === "{") {
            localStorage.setItem("method", resp.slice(1, i).join(" "))
            for (let ii = 1; ii < i; ii++) {
                resp[ii] = ""
            }
            break;
        }
    }
    resp = resp.join(" ")
    return resp;
}*/
