rootProject.name = "javap"
include(":javap-shared")
include(":javap-shared-js")
include(":javap-server")
include(":javap-client")
project(":javap-shared").projectDir = file("shared")
project(":javap-shared-js").projectDir = file("shared-js")
project(":javap-server").projectDir = file("server")
project(":javap-client").projectDir = file("client")
