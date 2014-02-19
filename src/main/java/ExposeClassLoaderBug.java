import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import hudson.*;

public class ExposeClassLoaderBug {

    private static final int NUM_THREADS = 3;

    public static void main(String[] args)
            throws Exception {

        String dir = args.length == 0 ? "./testData" : args[0];
        PluginManager pm = new PluginManager(null, new File(dir)) {
            @Override
            protected Collection<String> loadBundledPlugins() throws Exception {
                return new ArrayList<String>();
            }
        };
        String[] files = pm.rootDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".hpi");
            }
        });

        Map<String, PluginWrapper> wrappers = new HashMap<String, PluginWrapper>();
        for (String file: files) {
            PluginWrapper w = pm.getPluginStrategy().createPluginWrapper(new File(pm.rootDir, file));
            pm.getPlugins().add(w);
            wrappers.put(w.getShortName(), w);
        }

        final Map<String, PluginWrapper> finalWrappers = wrappers;
        for (int i=0; i<NUM_THREADS; i++) {
            final int count = i;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    PluginWrapper wrapper = null;
                    Class cls;
                    try {
                        if (count % 3 == 0) {
                            wrapper = finalWrappers.get("android-lint");
                            cls = wrapper.classLoader.loadClass("org.jenkinsci.plugins.android_lint.LintProjectAction");
                        } else if (count %3 == 1) {
                            wrapper = finalWrappers.get("findbugs");
                            cls = wrapper.classLoader.loadClass("hudson.plugins.findbugs.FindBugsProjectAction");
                        } else {
                            wrapper = finalWrappers.get("analysis-collector");
                            cls = wrapper.classLoader.loadClass("hudson.plugins.analysis.collector.AnalysisProjectAction");
                        }
                        //System.out.println("Thread " + Thread.currentThread().getName() + " loaded " + cls);
                    } catch (Throwable e) {
                        System.err.println("FAIL at thread:" + Thread.currentThread().getName() + " loading " + wrapper.getShortName());
                        System.err.println(e.getMessage());
                        //e.printStackTrace();
                        //e.getCause().printStackTrace();
                    }
                }
            });
            t.start();
        }
    }

}
