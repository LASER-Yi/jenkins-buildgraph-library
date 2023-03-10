import com.laseryi.buildgraph.ParallelController
import com.laseryi.buildgraph.PipelineConfiguration

void call(String path, Closure body) {
    skipDefaultCheckout(true)

    Map params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

    PipelineConfiguration config = new PipelineConfiguration(path)
    config.readFromParams(params)

    ParallelController controller = new ParallelController(config, this)

    controller.start()
}
