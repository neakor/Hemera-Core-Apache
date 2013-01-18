package hemera.core.apache;

import hemera.core.apache.runtime.ApacheRuntime;
import hemera.core.environment.config.Configuration;
import hemera.core.execution.interfaces.IExecutionService;
import hemera.core.structure.interfaces.runtime.IRuntime;
import hemera.core.structure.runtime.util.RuntimeLauncher;

/**
 * <code>ApacheRuntimeLauncher</code> defines the utility
 * runtime launching unit that uses Apache based runtime.
 *
 * @author Yi Wang (Neakor)
 * @version 1.0.0
 */
public class ApacheRuntimeLauncher extends RuntimeLauncher {

	@Override
	protected IRuntime newRuntime(final IExecutionService service, final Configuration config) {
		return new ApacheRuntime(service, config);
	}
}
