JAVAC = javac
JAVACDOC = javadoc
SOURCES = \
	Colors.java \
	Edge.java \
	NamedColor.java \
	Node.java \
	NodePlace.java \
	PlaceNode1.java \
	PlaceNode2.java \
	TreeBuild.java \
	TreeDisplayEvent.java \
	TreeDisplayListener.java \
	TreeVisualizer.java


CLASSFILES = $(SOURCES:.java=.class)
TOPDIR = ../../../..
DOCDIR = $(TOPDIR)/doc
PACKAGEDIR = weka/gui/treevisualizer
PACKAGE = weka.gui.treevisualizer

%.class : %.java
	$(JAVAC) $< 

.PHONY: doc

#----------------------------------------------------------------------------#

all :	$(CLASSFILES)

debug : clean
	$(JAVAC) -g $(SOURCES)

optimized : clean
	$(JAVAC) -O $(SOURCES)

fulljar : all
	(cd $(TOPDIR) ; \
	jar cvf archive/$(PACKAGE).`date +%d%b%Y`.jar \
	$(PACKAGEDIR); \
	rm -f $(PACKAGE).jar; \
	ln -s archive/$(PACKAGE).`date +%d%b%Y`.jar $(PACKAGE).jar)

classjar : all
	(cd $(TOPDIR) ;\
	jar cvf archive/$(PACKAGE)-bin.`date +%d%b%Y`.jar \
	$(PACKAGEDIR)/*.class; \
	rm -f $(PACKAGE).jar; \
	ln -s archive/$(PACKAGE)-bin.`date +%d%b%Y`.jar $(PACKAGE).jar)

doc : all
	$(JAVACDOC) -public -author -version -d $(DOCDIR) $(PACKAGEDIR)

clean : 
	$(RM) *.class *~

