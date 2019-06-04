COMPILE_DEPS = CORE_DEPS + KRYO + JACKSON + CLI + [
    "//core/common:onos-core-common", # For Comparators.PORT_COMPARATOR in BngPortTypeCommand
]

BUNDLES = [
    "//kpn/apps/aggregator:onos-kpn-apps-aggregator",
]


#osgi_jar(
#    deps = COMPILE_DEPS,
#)

osgi_jar_with_tests(
    karaf_command_packages = [
        "org.onosproject.aggregator.cli",
    ],
#    test_deps = TEST_DEPS,
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.aggregator",
    category = "Traffic Steering",
    description = "Marlou her new applications",
    included_bundles = BUNDLES,
    required_apps = [
    ],
    title = "Simple Aggregator",
    url = "https://marlou",
)
