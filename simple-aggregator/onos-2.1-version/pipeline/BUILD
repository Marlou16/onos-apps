COMPILE_DEPS = CORE_DEPS + KRYO + JACKSON + CLI + [
    "//protocols/p4runtime/model:onos-protocols-p4runtime-model",
    "//protocols/p4runtime/api:onos-protocols-p4runtime-api",
    "//providers/general/device:onos-providers-general-device",
    "//pipelines/basic:onos-pipelines-basic",
    "//core/store/serializers:onos-core-serializers",
    "//drivers/default:onos-drivers-default",
]

BUNDLES = [
    "//kpn/pipelines/aggregator:onos-kpn-pipelines-aggregator",
]

osgi_jar(
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.pipelines.aggregator",
    category = "Pipeline",
    description = "Fuck Franz Ferndinand",
    included_bundles = BUNDLES,
    required_apps = [
        "org.onosproject.drivers.p4runtime",
        "org.onosproject.pipelines.basic",
    ],
    title = "Aggregator",
    url = "https://kon.com/",
)
