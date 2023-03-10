import com.laseryi.buildgraph.SequentialController
import com.laseryi.buildgraph.PipelineConfiguration

void call(String path, Closure body) {
    skipDefaultCheckout(true)

    Map params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()

    PipelineConfiguration config = new PipelineConfiguration(path)
    config.readFromParams(params)

    SequentialController controller = new SequentialController(config, this)

    controller.start()
}
