/**
 * Copyright (c) 2017-present, Status Research and Development GmbH.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 *
 */

#ifndef RCTWKWEBVIEWMANAGER_H
#define RCTWKWEBVIEWMANAGER_H

#include "moduleinterface.h"
#include "componentmanagers/viewmanager.h"

class RCTWKWebViewManagerPrivate;
class RCTWKWebViewManager : public ViewManager {
    Q_OBJECT
    Q_INTERFACES(ModuleInterface)
    Q_DECLARE_PRIVATE(RCTWKWebViewManager)

public:
    Q_INVOKABLE RCTWKWebViewManager(QObject* parent = 0);
    ~RCTWKWebViewManager();

    virtual ViewManager* viewManager() override;
    virtual QString moduleName() override;

private:
    virtual void configureView(QQuickItem* view) const override;

    QScopedPointer<RCTWKWebViewManagerPrivate> d_ptr;
};

#endif // RCTWKWEBVIEWMANAGER_H
